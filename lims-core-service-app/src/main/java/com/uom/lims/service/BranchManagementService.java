package com.uom.lims.service;

import com.uom.lims.api.branch.dto.request.BranchUserCreateRequest;
import com.uom.lims.api.branch.dto.request.BranchUserUpdateRequest;
import com.uom.lims.api.branch.dto.response.BranchAuditLogResponse;
import com.uom.lims.api.branch.dto.response.BranchReportResponse;
import com.uom.lims.api.branch.dto.response.BranchUserResponse;
import com.uom.lims.api.branch.enums.AccountStatus;
import com.uom.lims.api.common.PageResponse;
import com.uom.lims.audit.AuditLog;
import com.uom.lims.audit.AuditLogRepository;
import com.uom.lims.entity.BranchEntity;
import com.uom.lims.entity.BranchUserEntity;
import com.uom.lims.exception.DuplicateResourceException;
import com.uom.lims.exception.ResourceNotFoundException;
import com.uom.lims.metadata.BranchRepository;
import com.uom.lims.repository.BranchUserRepository;
import com.uom.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * WHY: Centralises all branch-scoped user and reporting operations so that the
 * controller stays thin. Every mutating method resolves the caller's branch code
 * from the JWT at the service boundary — this guarantees a Branch Admin can never
 * create or modify users in a different branch, even with a crafted request body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BranchManagementService {

    private final BranchUserRepository branchUserRepository;
    private final BranchRepository branchRepository;
    private final AuditLogRepository auditLogRepository;

    // ------------------------------------------------------------------ Users

    @Transactional(readOnly = true)
    public PageResponse<BranchUserResponse> getBranchUsers(int page, int size) {
        String branchCode = resolveBranchCode();
        Page<BranchUserEntity> result = branchUserRepository.findAllByBranchCodeAndDeletedFalse(
                branchCode,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<BranchUserResponse> content = result.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    public BranchUserResponse createBranchUser(BranchUserCreateRequest request) {
        String branchCode = resolveBranchCode();

        if (branchUserRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new DuplicateResourceException(
                    "A user with username '" + request.getUsername() + "' already exists");
        }
        if (branchUserRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new DuplicateResourceException(
                    "A user with email '" + request.getEmail() + "' already exists");
        }

        BranchUserEntity entity = new BranchUserEntity();
        entity.setFullName(request.getFullName());
        entity.setEmail(request.getEmail());
        entity.setPhone(request.getPhone());
        entity.setUsername(request.getUsername());
        entity.setAccountStatus(request.getAccountStatus());
        entity.setRole(request.getRole());
        entity.setBranchCode(branchCode);

        BranchUserEntity saved = branchUserRepository.save(entity);
        log.info("Branch user created: id={} username={} branch={}", saved.getId(), saved.getUsername(), branchCode);
        return toResponse(saved);
    }

    public BranchUserResponse updateBranchUser(UUID userId, BranchUserUpdateRequest request) {
        String branchCode = resolveBranchCode();

        BranchUserEntity entity = branchUserRepository
                .findByIdAndBranchCodeAndDeletedFalse(userId, branchCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Branch user not found: " + userId));

        if (request.getEmail() != null
                && branchUserRepository.existsByEmailAndIdNotAndDeletedFalse(request.getEmail(), userId)) {
            throw new DuplicateResourceException(
                    "A user with email '" + request.getEmail() + "' already exists");
        }
        if (request.getFullName() != null) {
            entity.setFullName(request.getFullName());
        }
        if (request.getEmail() != null) {
            entity.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            entity.setPhone(request.getPhone());
        }
        if (request.getAccountStatus() != null) {
            entity.setAccountStatus(request.getAccountStatus());
        }
        if (request.getRole() != null) {
            entity.setRole(request.getRole());
        }

        BranchUserEntity saved = branchUserRepository.save(entity);
        log.info("Branch user updated: id={} branch={}", saved.getId(), branchCode);
        return toResponse(saved);
    }

    // --------------------------------------------------------------- Audit logs

    @Transactional(readOnly = true)
    public PageResponse<BranchAuditLogResponse> getBranchAuditLogs(
            String action, String entityType, String performedBy, String search, int page, int size) {

        String branchCode = resolveBranchCode();

        // Normalize blank strings to null so the repository query treats them as "no filter"
        action = normalise(action);
        entityType = normalise(entityType);
        performedBy = normalise(performedBy);
        search = normalise(search);

        Page<AuditLog> result = auditLogRepository.findByBranchCodeFiltered(
                branchCode, action, entityType, performedBy, search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));

        List<BranchAuditLogResponse> content = result.getContent().stream()
                .map(this::toAuditResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    // ----------------------------------------------------------------- Reports

    @Transactional(readOnly = true)
    public BranchReportResponse getBranchReport() {
        String branchCode = resolveBranchCode();

        BranchEntity branch = branchRepository.findByCode(branchCode)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchCode));

        long totalUsers = branchUserRepository.countByBranchCodeAndDeletedFalse(branchCode);
        long activeUsers = branchUserRepository.countByBranchCodeAndAccountStatusAndDeletedFalse(
                branchCode, AccountStatus.ACTIVE);
        long inactiveUsers = branchUserRepository.countByBranchCodeAndAccountStatusAndDeletedFalse(
                branchCode, AccountStatus.INACTIVE);
        long suspendedUsers = branchUserRepository.countByBranchCodeAndAccountStatusAndDeletedFalse(
                branchCode, AccountStatus.SUSPENDED);
        long totalAuditLogs = auditLogRepository.findByBranchCode(branchCode, PageRequest.of(0, 1))
                .getTotalElements();

        return BranchReportResponse.builder()
                .branchCode(branchCode)
                .branchName(branch.getName())
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .suspendedUsers(suspendedUsers)
                .totalAuditLogs(totalAuditLogs)
                .build();
    }

    // ----------------------------------------------------------------- Helpers

    /**
     * WHY: All service methods resolve the branch code once from the JWT rather than
     * accepting it as a parameter. This ensures a BRANCH_ADMIN cannot manipulate
     * data outside their own branch by passing a different branch code in the URL or body.
     */
    private String resolveBranchCode() {
        String branchCode = SecurityUtils.getCurrentBranchId();
        if (branchCode == null || branchCode.isBlank()) {
            throw new ResourceNotFoundException("Branch information is missing from your session token");
        }
        return branchCode;
    }

    private String normalise(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private BranchUserResponse toResponse(BranchUserEntity e) {
        return BranchUserResponse.builder()
                .id(e.getId())
                .fullName(e.getFullName())
                .email(e.getEmail())
                .phone(e.getPhone())
                .username(e.getUsername())
                .accountStatus(e.getAccountStatus())
                .role(e.getRole())
                .branchCode(e.getBranchCode())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getLastModifiedAt())
                .build();
    }

    private BranchAuditLogResponse toAuditResponse(AuditLog a) {
        BranchAuditLogResponse r = new BranchAuditLogResponse();
        r.setId(a.getId() != null ? a.getId().toString() : "");
        r.setAction(a.getAction());
        r.setEntityType(a.getEntityType());
        r.setEntityId(a.getEntityId() != null ? a.getEntityId().toString() : null);
        r.setPatientCode(a.getPatientCode());
        r.setPerformedBy(a.getPerformedBy());
        r.setBranchCode(a.getBranchCode());
        r.setIpAddress(a.getIpAddress());
        r.setTimestamp(a.getTimestamp() != null ? a.getTimestamp().toString() : "");
        r.setDetails(a.getDetails());
        return r;
    }
}
