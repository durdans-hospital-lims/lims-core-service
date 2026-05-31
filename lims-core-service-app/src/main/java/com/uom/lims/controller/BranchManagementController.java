package com.uom.lims.controller;

import com.uom.lims.api.branch.BranchManagementApi;
import com.uom.lims.api.branch.dto.request.BranchUserCreateRequest;
import com.uom.lims.api.branch.dto.request.BranchUserUpdateRequest;
import com.uom.lims.api.branch.dto.response.BranchAuditLogResponse;
import com.uom.lims.api.branch.dto.response.BranchReportResponse;
import com.uom.lims.api.branch.dto.response.BranchUserResponse;
import com.uom.lims.api.common.PageResponse;
import com.uom.lims.api.dto.response.ApiResponse;
import com.uom.lims.service.BranchManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * WHY: Every endpoint is guarded by @PreAuthorize("hasRole('BRANCH_ADMIN')") so that
 * only authenticated users carrying the BRANCH_ADMIN JWT role can call these operations.
 * The service layer additionally enforces that all data access is scoped to the caller's
 * own branch, providing defence-in-depth.
 */
@RestController
@RequiredArgsConstructor
public class BranchManagementController implements BranchManagementApi {

    private final BranchManagementService branchManagementService;

    @Override
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<BranchUserResponse>>> getBranchUsers(int page, int size) {
        return ResponseEntity.ok(ApiResponse.success(branchManagementService.getBranchUsers(page, size)));
    }

    @Override
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<BranchUserResponse>> createBranchUser(BranchUserCreateRequest request) {
        BranchUserResponse response = branchManagementService.createBranchUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Branch user created successfully"));
    }

    @Override
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<BranchUserResponse>> updateBranchUser(UUID userId, BranchUserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                branchManagementService.updateBranchUser(userId, request),
                "Branch user updated successfully"));
    }

    @Override
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<BranchAuditLogResponse>>> getBranchAuditLogs(
            String action, String entityType, String performedBy, String search, int page, int size) {
        return ResponseEntity.ok(ApiResponse.success(
                branchManagementService.getBranchAuditLogs(action, entityType, performedBy, search, page, size)));
    }

    @Override
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public ResponseEntity<ApiResponse<BranchReportResponse>> getBranchReport() {
        return ResponseEntity.ok(ApiResponse.success(branchManagementService.getBranchReport()));
    }
}
