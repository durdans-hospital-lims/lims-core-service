package com.uom.lims.service;

import com.uom.lims.api.dto.request.ResultItemRequest;
import com.uom.lims.api.dto.request.SampleRejectRequest;
import com.uom.lims.api.dto.request.SubmitResultsRequest;
import com.uom.lims.api.dto.response.MltWorklistItemResponse;
import com.uom.lims.api.dto.response.ResultParameterResponse;
import com.uom.lims.api.dto.response.SampleResultsResponse;
import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestParameterEntity;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.entity.TestCatalogEntity;
import com.uom.lims.exception.BusinessRuleException;
import com.uom.lims.repository.TestCatalogRepository;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestParameterRepository;
import com.uom.lims.repository.TestResultRepository;
import com.uom.lims.patient.PatientRepository;
import com.uom.lims.patient.PatientEntity;
import com.uom.lims.exception.ResourceNotFoundException;
import com.uom.lims.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MltTestingService {

        private final SampleRepository sampleRepository;
        private final TestParameterRepository parameterRepository;
        private final TestResultRepository resultRepository;
        private final TestCatalogRepository testCatalogRepository;
        private final PatientRepository patientRepository;
        private final SecurityUtils securityUtils;

        @Transactional(readOnly = true)
        public SampleResultsResponse getSampleResults(UUID sampleId) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Sample not found"));

                UUID testId = sample.getOrderItem().getTestId();
                TestCatalogEntity testCatalog = testCatalogRepository.findById(testId)
                                .orElseThrow(() -> new ResourceNotFoundException("Test catalog not found"));

                String patientName = patientRepository
                                .findByPatientCode(sample.getOrderItem().getOrder().getPatientId())
                                .map(PatientEntity::getFullName)
                                .orElse("UNKNOWN_PATIENT");

                List<TestParameterEntity> parameters = parameterRepository.findByTestIdOrderByDisplayOrderAsc(testId);
                Map<UUID, TestResultEntity> resultsByParameterId = resultRepository.findBySampleId(sample.getId()).stream()
                                .collect(Collectors.toMap(
                                                result -> result.getParameter().getId(),
                                                Function.identity(),
                                                (existing, replacement) -> replacement));

                List<ResultParameterResponse> resultResponses = parameters.stream()
                                .map(param -> {

                                        TestResultEntity result = resultsByParameterId.get(param.getId());

                                        return new ResultParameterResponse(
                                                        param.getId(),
                                                        param.getName(),
                                                        result != null ? result.getResultValue() : null,
                                                        param.getUnit(),
                                                        param.getRefLow(),
                                                        param.getRefHigh(),
                                                        result != null && result.getFlag() != null ? result.getFlag().name()
                                                                        : null);
                                })
                                .toList();
                String mltNotes = resultsByParameterId.values().stream()
                                .map(TestResultEntity::getMltNotes)
                                .filter(note -> note != null && !note.isBlank())
                                .findFirst()
                                .orElse(null);

                return new SampleResultsResponse(
                                sample.getId(),
                                sample.getBarcode(),
                                sample.getOrderItem().getOrder().getId(),
                                sample.getOrderItem().getId(),
                                patientName,
                                testCatalog.getTestName(),
                                sample.getStatus().name(),
                                resultResponses,
                                mltNotes);
        }

        @Transactional
        public void submitResults(UUID sampleId, SubmitResultsRequest request) {
                processResults(sampleId, request, false);
        }

        @Transactional
        public void saveDraftResults(UUID sampleId, SubmitResultsRequest request) {
                processResults(sampleId, request, true);
        }

        private void processResults(UUID sampleId, SubmitResultsRequest request, boolean isDraft) {
                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Sample not found"));

                if (isDraft) {
                        if (sample.getStatus() != SampleStatus.ACCEPTED
                                        && sample.getStatus() != SampleStatus.IN_TESTING) {
                                throw new BusinessRuleException(
                                                "Draft results can only be entered for ACCEPTED or IN_TESTING samples");
                        }
                } else if (sample.getStatus() != SampleStatus.IN_TESTING) {
                        throw new BusinessRuleException(
                                        "Final results can only be submitted for IN_TESTING samples");
                }

                if (!sample.getId().equals(request.sampleId())) {
                        throw new BusinessRuleException("Sample ID mismatch");
                }

                UUID sampleTestId = sample.getOrderItem().getTestId();
                List<TestParameterEntity> testParameters = parameterRepository.findByTestIdOrderByDisplayOrderAsc(sampleTestId);

                validateDuplicateParameterIds(request);

                if (!isDraft) {
                        validateFinalSubmissionCompleteness(request, testParameters);
                }

                for (ResultItemRequest item : request.results()) {

                        TestParameterEntity parameter = parameterRepository.findById(item.parameterId())
                                        .orElseThrow(() -> new ResourceNotFoundException("Parameter not found"));

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
                        result.setDraft(isDraft);
                        result.setFlag(resolveResultFlag(item, parameter));
                        result.setStatus(isDraft ? null : ResultStatus.ENTERED);

                        resultRepository.save(result);
                }

                if (isDraft && sample.getStatus() == SampleStatus.ACCEPTED) {
                        sample.setStatus(SampleStatus.IN_TESTING);
                        sampleRepository.save(sample);
                }

                if (!isDraft) {
                        sample.setStatus(SampleStatus.SENT_FOR_VERIFICATION);
                        sampleRepository.save(sample);
                }
        }

        private void validateFinalSubmissionCompleteness(SubmitResultsRequest request,
                        List<TestParameterEntity> testParameters) {
                Set<UUID> submittedParameterIds = request.results().stream()
                                .map(ResultItemRequest::parameterId)
                                .collect(Collectors.toCollection(HashSet::new));

                Set<UUID> expectedParameterIds = testParameters.stream()
                                .map(TestParameterEntity::getId)
                                .collect(Collectors.toSet());

                if (!submittedParameterIds.containsAll(expectedParameterIds)
                                || submittedParameterIds.size() != expectedParameterIds.size()) {
                        throw new BusinessRuleException(
                                        "All test parameters must be entered before final submission");
                }
        }

        private void validateDuplicateParameterIds(SubmitResultsRequest request) {
                Set<UUID> uniqueParameterIds = new HashSet<>();

                for (ResultItemRequest item : request.results()) {
                        if (!uniqueParameterIds.add(item.parameterId())) {
                                throw new BusinessRuleException(
                                                "Duplicate parameter entries are not allowed in the same submission");
                        }
                }
        }

        @Transactional(readOnly = true)
        public List<MltWorklistItemResponse> getWorklist() {
                return getWorklistByStatuses(List.of(
                                SampleStatus.ACCEPTED,
                                SampleStatus.IN_TESTING));
        }

        @Transactional(readOnly = true)
        public List<MltWorklistItemResponse> getCollectedSamples() {
                return getWorklistByStatuses(List.of(SampleStatus.COLLECTED));
        }

        private List<MltWorklistItemResponse> getWorklistByStatuses(List<SampleStatus> statuses) {

                List<SampleEntity> samples = sampleRepository
                                .findByStatusInAndDeletedFalseOrderByCollectedAtAsc(statuses);
                List<UUID> testIds = samples.stream()
                                .map(sample -> sample.getOrderItem().getTestId())
                                .distinct()
                                .toList();
                Map<UUID, String> testNameById = testCatalogRepository.findAllById(testIds).stream()
                                .collect(Collectors.toMap(
                                                TestCatalogEntity::getId,
                                                TestCatalogEntity::getTestName,
                                                (existing, replacement) -> existing));

                return samples.stream()
                                .map(sample -> new MltWorklistItemResponse(
                                                sample.getId(),
                                                sample.getBarcode(),
                                                sample.getOrderItem().getOrder().getId(),
                                                sample.getOrderItem().getId(),
                                                sample.getOrderItem().getOrder().getPatientId(),
                                                testNameById.getOrDefault(sample.getOrderItem().getTestId(),
                                                                "UNKNOWN_TEST"),
                                                sample.getPriority().name(),
                                                sample.getStatus().name(),
                                                sample.getCollectedAt()))
                                .toList();
        }

        @Transactional
        public void acceptSample(UUID sampleId) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Sample not found"));

                if (sample.getStatus() != SampleStatus.COLLECTED) {
                        throw new BusinessRuleException("Only COLLECTED samples can be accepted");
                }

                sample.setStatus(SampleStatus.ACCEPTED);
                sampleRepository.save(sample);
        }

        @Transactional
        public void rejectSample(UUID sampleId, SampleRejectRequest request) {

                SampleEntity sample = sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Sample not found"));

                if (sample.getStatus() != SampleStatus.COLLECTED) {
                        throw new BusinessRuleException("Only COLLECTED samples can be rejected");
                }

                if (request.getRejectionReason() == com.uom.lims.api.enums.RejectionReason.OTHER
                                && (request.getRejectionNotes() == null || request.getRejectionNotes().isBlank())) {
                        throw new BusinessRuleException("Rejection notes are mandatory when rejection reason is OTHER");
                }

                sample.setStatus(SampleStatus.REJECTED);
                sample.setRejectionReason(request.getRejectionReason());
                sample.setRejectionNotes(request.getRejectionNotes());
                sample.setRejectedAt(Instant.now());
                sample.setRejectedBy(securityUtils.getCurrentUsername());

                sampleRepository.save(sample);
        }

        private ResultFlag resolveResultFlag(ResultItemRequest item, TestParameterEntity parameter) {
                BigDecimal numericResult = parseNumericResult(item.result());

                if (numericResult != null) {
                        if (parameter.getRefLow() != null && numericResult.compareTo(parameter.getRefLow()) < 0) {
                                return ResultFlag.LOW;
                        }

                        if (parameter.getRefHigh() != null && numericResult.compareTo(parameter.getRefHigh()) > 0) {
                                return ResultFlag.HIGH;
                        }

                        if (parameter.getRefLow() != null || parameter.getRefHigh() != null) {
                                return ResultFlag.NORMAL;
                        }
                }

                if (item.flag() != null && !item.flag().isBlank()) {
                        try {
                                return ResultFlag.valueOf(item.flag().trim().toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException ex) {
                                throw new BusinessRuleException("Invalid result flag: " + item.flag());
                        }
                }

                return null;
        }

        private BigDecimal parseNumericResult(String resultValue) {
                if (resultValue == null || resultValue.isBlank()) {
                        return null;
                }

                try {
                        return new BigDecimal(resultValue.trim());
                } catch (NumberFormatException ex) {
                        return null;
                }
        }
}
