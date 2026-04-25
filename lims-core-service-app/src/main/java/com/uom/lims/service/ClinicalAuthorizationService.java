package com.uom.lims.service;

import com.uom.lims.api.clinical.dto.request.ClinicalAuthRequest;
import com.uom.lims.api.clinical.dto.request.ReturnToMLTRequest;
import com.uom.lims.api.verification.dto.response.TestResultDetailResponse;
import com.uom.lims.api.verification.dto.response.TestResultSummaryResponse;
import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.entity.TestResultParameterEntity;
import com.uom.lims.mapper.TestResultMapper;
import com.uom.lims.repository.TestResultParameterRepository;
import com.uom.lims.repository.TestResultRepository;
import com.uom.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ClinicalAuthorizationService {

    private final TestResultRepository testResultRepository;
    private final TestResultParameterRepository testResultParameterRepository;
    private final TestResultMapper testResultMapper;

    @Transactional(readOnly = true)
    public Page<TestResultSummaryResponse> getPendingResults(int page, int size) {
        return testResultRepository
                .findByStatus(ResultStatus.TECHNICALLY_VERIFIED, PageRequest.of(page, size))
                .map(testResultMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public TestResultDetailResponse getResultDetails(UUID resultId) {
        TestResultEntity result = findResultById(resultId);
        List<TestResultParameterEntity> parameters =
                testResultParameterRepository.findByTestResultOrderBySortOrderAsc(result);

        return testResultMapper.toDetailResponse(result, parameters);
    }

    @Transactional
    public TestResultDetailResponse authorizeResult(UUID resultId, ClinicalAuthRequest request) {
        TestResultEntity result = findResultById(resultId);

        if (result.getStatus() != ResultStatus.TECHNICALLY_VERIFIED) {
            throw new IllegalStateException(
                    "Cannot authorize result not in TECHNICALLY_VERIFIED status. Current: "
                            + result.getStatus());
        }

        String username = SecurityUtils.getCurrentUsername();
        Instant now = Instant.now();

        result.setStatus(ResultStatus.CLINICALLY_AUTHORIZED);
        result.setClinicalNote(request.getClinicalNote());
        result.setClinicallyAuthorizedBy(username);
        result.setClinicallyAuthorizedAt(now);
        result.setUpdatedBy(username);
        result.setUpdatedAt(now);

        TestResultEntity saved = testResultRepository.save(result);
        List<TestResultParameterEntity> parameters =
                testResultParameterRepository.findByTestResultOrderBySortOrderAsc(saved);

        return testResultMapper.toDetailResponse(saved, parameters);
    }

    @Transactional
    public TestResultDetailResponse returnToMlt(UUID resultId, ReturnToMLTRequest request) {
        TestResultEntity result = findResultById(resultId);

        if (result.getStatus() != ResultStatus.TECHNICALLY_VERIFIED) {
            throw new IllegalStateException(
                    "Cannot return result not in TECHNICALLY_VERIFIED status. Current: "
                            + result.getStatus());
        }

        String username = SecurityUtils.getCurrentUsername();
        Instant now = Instant.now();

        result.setStatus(ResultStatus.RETURNED);
        result.setReturnReason(request.getReturnReason());
        result.setReturnedBy(username);
        result.setReturnedAt(now);
        result.setUpdatedBy(username);
        result.setUpdatedAt(now);

        TestResultEntity saved = testResultRepository.save(result);
        List<TestResultParameterEntity> parameters =
                testResultParameterRepository.findByTestResultOrderBySortOrderAsc(saved);

        return testResultMapper.toDetailResponse(saved, parameters);
    }

    private TestResultEntity findResultById(UUID id) {
        return testResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test result not found: " + id));
    }
}