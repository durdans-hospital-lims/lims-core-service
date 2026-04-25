package com.uom.lims.service;

import com.uom.lims.api.verification.dto.request.BulkVerificationRequest;
import com.uom.lims.api.verification.dto.request.VerificationRequest;
import com.uom.lims.api.verification.dto.response.TestResultDetailResponse;
import com.uom.lims.api.verification.dto.response.TestResultSummaryResponse;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.mapper.TestResultMapper;
import com.uom.lims.repository.TestResultRepository;
import com.uom.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class VerificationService {

    private final TestResultRepository testResultRepository;
    private final TestResultMapper testResultMapper;

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

    private TestResultEntity findResultById(UUID id) {
        return testResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test result not found: " + id));
    }
}
