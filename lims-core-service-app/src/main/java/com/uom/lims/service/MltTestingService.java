package com.uom.lims.service;

import com.uom.lims.api.dto.request.ResultItemRequest;
import com.uom.lims.api.dto.request.SubmitResultsRequest;
import com.uom.lims.api.dto.response.ResultParameterResponse;
import com.uom.lims.api.dto.response.SampleResultsResponse;
import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestParameterEntity;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestParameterRepository;
import com.uom.lims.repository.TestResultRepository;
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

        @Transactional(readOnly = true)
        public SampleResultsResponse getSampleResults(UUID sampleId) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new RuntimeException("Sample not found"));

                UUID testId = sample.getOrderItem().getTestId();

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
                                "TEST_NAME",
                                sample.getStatus().name(),
                                resultResponses,
                                null);
        }

        @Transactional
        public void submitResults(UUID sampleId, SubmitResultsRequest request) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new RuntimeException("Sample not found"));

                if (!sample.getId().equals(request.sampleId())) {
                        throw new RuntimeException("Sample ID mismatch");
                }

                for (ResultItemRequest item : request.results()) {

                        TestParameterEntity parameter = parameterRepository.findById(item.parameterId())
                                        .orElseThrow(() -> new RuntimeException("Parameter not found"));

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
}