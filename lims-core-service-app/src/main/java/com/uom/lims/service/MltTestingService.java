package com.uom.lims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uom.lims.api.dto.request.ResultItemRequest;
import com.uom.lims.api.dto.request.SampleRejectRequest;
import com.uom.lims.api.dto.response.MltAllWorklistItemResponse;
import com.uom.lims.api.dto.request.SubmitResultsRequest;
import com.uom.lims.api.dto.response.MltResultActivityItemResponse;
import com.uom.lims.api.dto.response.MltWorklistItemResponse;
import com.uom.lims.api.dto.response.PreviousValueResponse;
import com.uom.lims.api.dto.response.ResultParameterResponse;
import com.uom.lims.api.dto.response.SampleResultsResponse;
import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.audit.AuditLogRepository;
import com.uom.lims.audit.AuditService;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MltTestingService {

        private static final String MLT_ENTITY_TYPE = "MLT_RESULT_ENTRY";
        private static final String ACTION_DRAFT = "MLT_RESULTS_DRAFT_SAVED";
        private static final String ACTION_SUBMIT = "MLT_RESULTS_SUBMITTED";

        private static final EnumSet<ResultStatus> PREVIOUS_VALUE_STATUSES = EnumSet.of(
                        ResultStatus.CLINICALLY_AUTHORIZED,
                        ResultStatus.TECHNICALLY_VERIFIED,
                        ResultStatus.DISPATCHED);

        private final SampleRepository sampleRepository;
        private final TestParameterRepository parameterRepository;
        private final TestResultRepository resultRepository;
        private final TestCatalogRepository testCatalogRepository;
        private final PatientRepository patientRepository;
        private final SecurityUtils securityUtils;
        private final AuditService auditService;
        private final AuditLogRepository auditLogRepository;
        private final ObjectMapper objectMapper;

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

                String patientCode = sample.getOrderItem().getOrder().getPatientId();
                Instant visitAt = sample.getCollectedAt() != null ? sample.getCollectedAt()
                                : sample.getCreatedAt();
                Map<UUID, TestResultEntity> previousByParameterId = resolvePreviousAuthorizedResults(
                                patientCode,
                                sample.getOrderItem().getTestId(),
                                sample.getId(),
                                visitAt);

                List<ResultParameterResponse> resultResponses = parameters.stream()
                                .map(param -> {

                                        TestResultEntity result = resultsByParameterId.get(param.getId());
                                        PreviousValueResponse previousValue = toPreviousValueResponse(
                                                        previousByParameterId.get(param.getId()));

                                        return new ResultParameterResponse(
                                                        param.getId(),
                                                        param.getName(),
                                                        result != null ? result.getResultValue() : null,
                                                        param.getUnit(),
                                                        param.getRefLow(),
                                                        param.getRefHigh(),
                                                        result != null && result.getFlag() != null ? result.getFlag().name()
                                                                        : null,
                                                        previousValue);
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
                                sample.getOrderItem().getOrder().getOrderNo(),
                                sample.getOrderItem().getId(),
                                patientCode,
                                patientName,
                                testCatalog.getTestName(),
                                sample.getStatus().name(),
                                sample.getTubeType().name(),
                                sample.getPriority().name(),
                                sample.getCollectedAt(),
                                sample.getCollectedBy(),
                                resultResponses,
                                mltNotes);
        }

        @Transactional(readOnly = true)
        public List<MltResultActivityItemResponse> getSampleResultActivity(UUID sampleId) {
                sampleRepository.findById(sampleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Sample not found"));

                return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(MLT_ENTITY_TYPE, sampleId)
                                .stream()
                                .map(log -> new MltResultActivityItemResponse(
                                                log.getId(),
                                                log.getAction(),
                                                log.getPerformedBy(),
                                                log.getTimestamp(),
                                                log.getDetails()))
                                .toList();
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

                Map<UUID, String> parameterNames = testParameters.stream()
                                .collect(Collectors.toMap(TestParameterEntity::getId, TestParameterEntity::getName));

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

                if (!request.results().isEmpty()) {
                        auditService.log(
                                        isDraft ? ACTION_DRAFT : ACTION_SUBMIT,
                                        MLT_ENTITY_TYPE,
                                        sample.getId(),
                                        sample.getOrderItem().getOrder().getPatientId(),
                                        buildMltResultAuditPayload(sample, request, isDraft, parameterNames),
                                        null);
                }
        }

        private Map<UUID, TestResultEntity> resolvePreviousAuthorizedResults(
                        String patientId,
                        UUID testId,
                        UUID sampleId,
                        Instant currentVisitAt) {

                List<TestResultEntity> rows = resultRepository.findPreviousResultsForPatientAndTest(
                                patientId,
                                testId,
                                sampleId,
                                currentVisitAt);

                Map<UUID, TestResultEntity> pickFirst = new LinkedHashMap<>();
                for (TestResultEntity row : rows) {
                        if (row.getStatus() == null || !PREVIOUS_VALUE_STATUSES.contains(row.getStatus())) {
                                continue;
                        }
                        UUID parameterId = row.getParameter().getId();
                        pickFirst.putIfAbsent(parameterId, row);
                }
                return pickFirst;
        }

        private PreviousValueResponse toPreviousValueResponse(TestResultEntity entity) {
                if (entity == null) {
                        return null;
                }

                SampleEntity priorSample = entity.getSample();
                Instant at = priorSample.getCollectedAt() != null ? priorSample.getCollectedAt()
                                : priorSample.getCreatedAt();
                String flag = entity.getFlag() != null ? entity.getFlag().name() : null;

                return new PreviousValueResponse(entity.getResultValue(), flag, at, priorSample.getBarcode());
        }

        private String buildMltResultAuditPayload(
                        SampleEntity sample,
                        SubmitResultsRequest request,
                        boolean isDraft,
                        Map<UUID, String> parameterNames) {

                String testName = testCatalogRepository.findById(sample.getOrderItem().getTestId())
                                .map(TestCatalogEntity::getTestName)
                                .orElse("UNKNOWN_TEST");

                List<Map<String, Object>> parameterRows = new ArrayList<>();
                for (ResultItemRequest item : request.results()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("parameterName", parameterNames.getOrDefault(item.parameterId(),
                                        item.parameterId().toString()));
                        row.put("value", item.result());
                        row.put("submittedFlag", item.flag());
                        parameterRows.add(row);
                }

                Map<String, Object> root = new LinkedHashMap<>();
                root.put("barcode", sample.getBarcode());
                root.put("testName", testName);
                root.put("draft", isDraft);
                root.put("mltNotes", request.mltNotes());
                root.put("parameters", parameterRows);

                try {
                        return objectMapper.writeValueAsString(root);
                } catch (JsonProcessingException exception) {
                        throw new BusinessRuleException("Could not create MLT audit payload");
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

        @Transactional(readOnly = true)
        public List<MltAllWorklistItemResponse> getAllWorklist() {
                List<SampleEntity> samples = sampleRepository.findAllMltWorklistSamples();

                List<UUID> testIds = samples.stream()
                                .map(sample -> sample.getOrderItem().getTestId())
                                .distinct()
                                .toList();

                Map<UUID, TestCatalogEntity> testsById = testCatalogRepository.findAllById(testIds).stream()
                                .collect(Collectors.toMap(
                                                TestCatalogEntity::getId,
                                                Function.identity(),
                                                (existing, replacement) -> existing));

                List<String> patientCodes = samples.stream()
                                .map(sample -> sample.getOrderItem().getOrder().getPatientId())
                                .distinct()
                                .toList();

                Map<String, String> patientNameByCode = patientRepository.findAll().stream()
                                .filter(patient -> patientCodes.contains(patient.getPatientCode()))
                                .collect(Collectors.toMap(
                                                PatientEntity::getPatientCode,
                                                PatientEntity::getFullName,
                                                (existing, replacement) -> existing));

                return samples.stream()
                                .map(sample -> {
                                        TestCatalogEntity testCatalog = testsById.get(sample.getOrderItem().getTestId());
                                        String patientCode = sample.getOrderItem().getOrder().getPatientId();

                                        return new MltAllWorklistItemResponse(
                                                        sample.getId(),
                                                        sample.getBarcode(),
                                                        sample.getOrderItem().getOrder().getOrderNo(),
                                                        patientCode,
                                                        patientNameByCode.getOrDefault(patientCode, "UNKNOWN_PATIENT"),
                                                        testCatalog != null ? testCatalog.getTestName() : "UNKNOWN_TEST",
                                                        testCatalog != null ? testCatalog.getCategory() : "General",
                                                        sample.getPriority().name(),
                                                        sample.getStatus().name(),
                                                        sample.getCollectedAt());
                                })
                                .toList();
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
                                                sample.getOrderItem().getOrder().getOrderNo(),
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

                auditService.log(
                                "ACCEPTED",
                                "SAMPLE_ACCESSIONING",
                                sample.getId(),
                                sample.getOrderItem().getOrder().getPatientId(),
                                buildAccessioningAuditDetails(sample, SampleStatus.ACCEPTED, null),
                                null);
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

                auditService.log(
                                "REJECTED",
                                "SAMPLE_ACCESSIONING",
                                sample.getId(),
                                sample.getOrderItem().getOrder().getPatientId(),
                                buildAccessioningAuditDetails(sample, SampleStatus.REJECTED, request.getRejectionNotes()),
                                null);
        }

        private String buildAccessioningAuditDetails(SampleEntity sample, SampleStatus status, String notes) {
                String patientCode = sample.getOrderItem().getOrder().getPatientId();
                String patientName = patientRepository.findByPatientCode(patientCode)
                                .map(PatientEntity::getFullName)
                                .orElse("UNKNOWN_PATIENT");
                String testName = testCatalogRepository.findById(sample.getOrderItem().getTestId())
                                .map(TestCatalogEntity::getTestName)
                                .orElse("UNKNOWN_TEST");

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("sampleId", sample.getBarcode());
                details.put("patientName", patientName);
                details.put("pid", patientCode);
                details.put("testType", testName);
                details.put("priority", sample.getPriority().name());
                details.put("status", status.name());
                details.put("notes", notes);
                if (sample.getRejectionReason() != null) {
                        details.put("rejectionReason", sample.getRejectionReason().name());
                }

                try {
                        return objectMapper.writeValueAsString(details);
                } catch (JsonProcessingException exception) {
                        throw new BusinessRuleException("Could not create accessioning audit log details");
                }
        }

        private ResultFlag resolveResultFlag(ResultItemRequest item, TestParameterEntity parameter) {
                ResultFlag submittedFlag = null;
                if (item.flag() != null && !item.flag().isBlank()) {
                        try {
                                submittedFlag = ResultFlag.valueOf(item.flag().trim().toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException ex) {
                                throw new BusinessRuleException("Invalid result flag: " + item.flag());
                        }

                        if (submittedFlag == ResultFlag.CRITICAL_HIGH || submittedFlag == ResultFlag.CRITICAL_LOW) {
                                return submittedFlag;
                        }
                }

                BigDecimal numericResult = parseNumericResult(item.result());

                if (numericResult != null) {
                        if (parameter.getRefLow() != null && numericResult.compareTo(parameter.getRefLow()) < 0) {
                                BigDecimal criticalLow = parameter.getRefLow().multiply(new BigDecimal("0.70"));
                                if (numericResult.compareTo(criticalLow) < 0) {
                                        return ResultFlag.CRITICAL_LOW;
                                }
                                return ResultFlag.LOW;
                        }

                        if (parameter.getRefHigh() != null && numericResult.compareTo(parameter.getRefHigh()) > 0) {
                                BigDecimal criticalHigh = parameter.getRefHigh().multiply(new BigDecimal("1.30"));
                                if (numericResult.compareTo(criticalHigh) > 0) {
                                        return ResultFlag.CRITICAL_HIGH;
                                }
                                return ResultFlag.HIGH;
                        }

                        if (parameter.getRefLow() != null || parameter.getRefHigh() != null) {
                                return ResultFlag.NORMAL;
                        }
                }

                return submittedFlag;
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
