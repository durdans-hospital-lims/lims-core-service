package com.uom.lims.service;

import com.uom.lims.api.dto.response.VerificationPendingItemResponse;
import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.api.verification.dto.request.BulkVerificationRequest;
import com.uom.lims.api.verification.dto.request.VerificationRequest;
import com.uom.lims.api.verification.dto.response.TestResultDetailResponse;
import com.uom.lims.api.verification.dto.response.TestResultSummaryResponse;
import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestCatalogEntity;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.mapper.TestResultMapper;
import com.uom.lims.patient.PatientEntity;
import com.uom.lims.patient.PatientRepository;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestCatalogRepository;
import com.uom.lims.repository.TestResultRepository;
import com.uom.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class VerificationService {

    private final SampleRepository sampleRepository;
    private final TestCatalogRepository testCatalogRepository;
    private final TestResultRepository testResultRepository;
    private final PatientRepository patientRepository;
    private final TestResultMapper testResultMapper;

    @Transactional(readOnly = true)
    public List<VerificationPendingItemResponse> getPendingSamples() {
        List<SampleEntity> samples = sampleRepository.findByStatusInAndDeletedFalseOrderByCollectedAtAsc(
                List.of(SampleStatus.SENT_FOR_VERIFICATION));

        List<UUID> testIds = samples.stream()
                .map(sample -> sample.getOrderItem().getTestId())
                .distinct()
                .toList();

        Map<UUID, String> testNameById = testCatalogRepository.findAllById(testIds).stream()
                .collect(Collectors.toMap(
                        TestCatalogEntity::getId,
                        TestCatalogEntity::getTestName,
                        (existing, replacement) -> existing));

        List<UUID> sampleIds = samples.stream()
                .map(SampleEntity::getId)
                .toList();

        Map<UUID, List<TestResultEntity>> resultsBySampleId = testResultRepository.findBySampleIdIn(sampleIds)
                .stream()
                .collect(Collectors.groupingBy(result -> result.getSample().getId()));

        Map<String, String> patientNameByCode = samples.stream()
                .map(sample -> sample.getOrderItem().getOrder().getPatientId())
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        patientCode -> patientRepository.findByPatientCode(patientCode)
                                .map(PatientEntity::getFullName)
                                .orElse("UNKNOWN_PATIENT")));

        return samples.stream()
                .map(sample -> {
                    List<TestResultEntity> results = resultsBySampleId.getOrDefault(sample.getId(), List.of());
                    TestResultEntity latestResult = results.stream()
                            .max(Comparator.comparing(
                                    result -> result.getLastModifiedAt() != null
                                            ? result.getLastModifiedAt()
                                            : result.getCreatedAt()))
                            .orElse(null);

                    ResultFlag overallFlag = results.stream()
                            .map(TestResultEntity::getFlag)
                            .filter(flag -> flag != null)
                            .max(Comparator.comparingInt(this::flagSeverity))
                            .orElse(null);

                    return new VerificationPendingItemResponse(
                            sample.getId(),
                            sample.getBarcode(),
                            sample.getOrderItem().getOrder().getId(),
                            sample.getOrderItem().getOrder().getPatientId(),
                            patientNameByCode.getOrDefault(
                                    sample.getOrderItem().getOrder().getPatientId(),
                                    "UNKNOWN_PATIENT"),
                            testNameById.getOrDefault(sample.getOrderItem().getTestId(), "UNKNOWN_TEST"),
                            sample.getPriority().name(),
                            sample.getStatus().name(),
                            overallFlag != null ? overallFlag.name() : null,
                            latestResult != null
                                    ? (latestResult.getLastModifiedBy() != null
                                            ? latestResult.getLastModifiedBy()
                                            : latestResult.getCreatedBy())
                                    : null,
                            sample.getLastModifiedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TestResultSummaryResponse> getPendingResults(int page, int size) {
        return testResultRepository
                .findByStatusAndDraftFalse(ResultStatus.ENTERED, PageRequest.of(page, size))
                .map(testResultMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public TestResultDetailResponse getResultDetails(UUID resultId) {
        TestResultEntity result = findResultById(resultId);
        return testResultMapper.toDetailResponse(result);
    }

    @Transactional
    public TestResultDetailResponse verifyResult(UUID resultId, VerificationRequest request) {
        TestResultEntity result = findResultById(resultId);

        if (result.getStatus() != ResultStatus.ENTERED) {
            throw new IllegalStateException(
                    "Cannot verify result not in ENTERED status. Current: " + result.getStatus());
        }

        String username = SecurityUtils.getCurrentUsername();
        Instant now = Instant.now();

        result.setStatus(ResultStatus.TECHNICALLY_VERIFIED);
        result.getSample().setStatus(SampleStatus.VERIFIED);
        result.setMltNotes(request.getMltNotes());
        result.setTechnicallyVerifiedBy(username);
        result.setTechnicallyVerifiedAt(now);
        result.setLastModifiedBy(username);
        result.setLastModifiedAt(now);

        TestResultEntity saved = testResultRepository.save(result);
        return testResultMapper.toDetailResponse(saved);
    }

    @Transactional
    public TestResultDetailResponse rejectResult(UUID resultId, VerificationRequest request) {
        TestResultEntity result = findResultById(resultId);

        if (result.getStatus() != ResultStatus.ENTERED) {
            throw new IllegalStateException(
                    "Cannot reject result not in ENTERED status. Current: " + result.getStatus());
        }

        String username = SecurityUtils.getCurrentUsername();
        Instant now = Instant.now();

        result.setStatus(ResultStatus.REJECTED);
        result.getSample().setStatus(SampleStatus.REJECTED);
        result.setMltNotes(request.getMltNotes());
        result.setTechnicallyVerifiedBy(username);
        result.setTechnicallyVerifiedAt(now);
        result.setLastModifiedBy(username);
        result.setLastModifiedAt(now);

        TestResultEntity saved = testResultRepository.save(result);
        return testResultMapper.toDetailResponse(saved);
    }

    @Transactional
    public Map<String, String> bulkVerify(BulkVerificationRequest request) {
        Map<String, String> resultMap = new LinkedHashMap<>();

        for (String resultIdValue : request.getResultIds()) {
            try {
                UUID resultId = UUID.fromString(resultIdValue);
                VerificationRequest verificationRequest = VerificationRequest.builder()
                        .mltNotes(request.getMltNotes())
                        .build();

                verifyResult(resultId, verificationRequest);
                resultMap.put(resultIdValue, "VERIFIED");
            } catch (Exception exception) {
                resultMap.put(resultIdValue, "FAILED: " + exception.getMessage());
            }
        }

        return resultMap;
    }

    private int flagSeverity(ResultFlag flag) {
        return switch (flag) {
            case NORMAL -> 0;
            case LOW, HIGH -> 1;
            case CRITICAL_LOW, CRITICAL_HIGH -> 2;
        };
    }

    private TestResultEntity findResultById(UUID id) {
        return testResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test result not found: " + id));
    }
}
