package com.uom.lims.service;

import com.uom.lims.api.dto.request.ResultItemRequest;
import com.uom.lims.api.dto.request.SampleRejectRequest;
import com.uom.lims.api.dto.request.SubmitResultsRequest;
import com.uom.lims.api.dto.response.MltWorklistItemResponse;
import com.uom.lims.api.dto.response.ResultParameterResponse;
import com.uom.lims.api.dto.response.SampleResultsResponse;
import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestParameterEntity;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.entity.TestCatalogEntity;
import com.uom.lims.repository.TestCatalogRepository;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestParameterRepository;
import com.uom.lims.repository.TestResultRepository;
import com.uom.lims.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MltTestingService {

        private final SampleRepository sampleRepository;
        private final TestParameterRepository parameterRepository;
        private final TestResultRepository resultRepository;
        private final TestCatalogRepository testCatalogRepository;

        @Transactional(readOnly = true)
        public SampleResultsResponse getSampleResults(UUID sampleId) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new RuntimeException("Sample not found"));

                UUID testId = sample.getOrderItem().getTestId();
                TestCatalogEntity testCatalog = testCatalogRepository.findById(testId)
                                .orElseThrow(() -> new RuntimeException("Test catalog not found"));

                List<TestParameterEntity> parameters = parameterRepository.findByTestIdOrderByDisplayOrderAsc(testId);

                List<ResultParameterResponse> resultResponses = parameters.stream()
                                .map(param -> {

                                        var resultOpt = resultRepository
                                                        .findBySampleIdAndParameterId(sample.getId(), param.getId());

                                        return new ResultParameterResponse(
                                                        param.getId(),
                                                        param.getName(),
                                                        resultOpt.map(TestResultEntity::getResultValue).orElse(null),
                                                        param.getUnit(),
                                                        param.getRefLow(),
                                                        param.getRefHigh(),
                                                        resultOpt.map(r -> r.getFlag() != null ? r.getFlag().name()
                                                                        : null).orElse(null));
                                })
                                .toList();

                return new SampleResultsResponse(
                                sample.getId(),
                                sample.getBarcode(),
                                sample.getOrderItem().getOrder().getId(),
                                sample.getOrderItem().getId(),
                                sample.getOrderItem().getOrder().getPatientId(),
                                testCatalog.getTestName(),
                                sample.getStatus().name(),
                                resultResponses,
                                null);
        }

        @Transactional
        public void submitResults(UUID sampleId, SubmitResultsRequest request) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new RuntimeException("Sample not found"));

                if (sample.getStatus() != SampleStatus.ACCEPTED && sample.getStatus() != SampleStatus.IN_TESTING) {
                        throw new BusinessRuleException(
                                        "Results can only be submitted for ACCEPTED or IN_TESTING samples");
                }

                if (!sample.getId().equals(request.sampleId())) {
                        throw new BusinessRuleException("Sample ID mismatch");
                }

                for (ResultItemRequest item : request.results()) {

                        TestParameterEntity parameter = parameterRepository.findById(item.parameterId())
                                        .orElseThrow(() -> new RuntimeException("Parameter not found"));

                        UUID sampleTestId = sample.getOrderItem().getTestId();

                        if (!parameter.getTestId().equals(sampleTestId)) {
                                throw new BusinessRuleException("Parameter does not belong to the sample's test");
                        }

                        TestResultEntity result = resultRepository
                                        .findBySampleIdAndParameterId(sample.getId(), parameter.getId())
                                        .orElseGet(TestResultEntity::new);

                        result.setSample(sample);
                        result.setParameter(parameter);
                        result.setResultValue(item.result());
                        result.setMltNotes(request.mltNotes());
                        result.setDraft(false);

                        if (item.flag() != null && !item.flag().isBlank()) {
                                result.setFlag(ResultFlag.valueOf(item.flag()));
                        } else {
                                result.setFlag(null);
                        }

                        resultRepository.save(result);
                }

                sample.setStatus(SampleStatus.RESULT_ENTERED);
                sampleRepository.save(sample);
        }

        @Transactional(readOnly = true)
        public List<MltWorklistItemResponse> getWorklist() {

                List<SampleStatus> statuses = List.of(
                                SampleStatus.COLLECTED,
                                SampleStatus.ACCEPTED,
                                SampleStatus.IN_TESTING);

                List<SampleEntity> samples = sampleRepository
                                .findByStatusInAndDeletedFalseOrderByCollectedAtAsc(statuses);

                return samples.stream()
                                .map(sample -> new MltWorklistItemResponse(
                                                sample.getId(),
                                                sample.getBarcode(),
                                                sample.getOrderItem().getOrder().getId(),
                                                sample.getOrderItem().getId(),
                                                sample.getOrderItem().getOrder().getPatientId(),
                                                testCatalogRepository.findById(sample.getOrderItem().getTestId())
                                                                .map(TestCatalogEntity::getTestName)
                                                                .orElse("UNKNOWN_TEST"),
                                                sample.getPriority().name(),
                                                sample.getStatus().name(),
                                                sample.getCollectedAt()))
                                .toList();
        }

        @Transactional
        public void acceptSample(UUID sampleId) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new RuntimeException("Sample not found"));

                if (sample.getStatus() != SampleStatus.COLLECTED) {
                        throw new BusinessRuleException("Only COLLECTED samples can be accepted");
                }

                sample.setStatus(SampleStatus.ACCEPTED);
                sampleRepository.save(sample);
        }

        @Transactional
        public void rejectSample(UUID sampleId, SampleRejectRequest request) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new RuntimeException("Sample not found"));

                if (sample.getStatus() != SampleStatus.COLLECTED) {
                        throw new BusinessRuleException("Only COLLECTED samples can be rejected");
                }

                sample.setStatus(SampleStatus.REJECTED);
                sample.setRejectionReason(request.getRejectionReason());
                sample.setRejectionNotes(request.getRejectionNotes());

                sampleRepository.save(sample);
        }
}