package com.uom.lims.api.branch;

import com.uom.lims.api.branch.dto.request.BranchUserCreateRequest;
import com.uom.lims.api.branch.dto.request.BranchUserUpdateRequest;
import com.uom.lims.api.branch.dto.response.BranchAuditLogResponse;
import com.uom.lims.api.branch.dto.response.BranchReportResponse;
import com.uom.lims.api.branch.dto.response.BranchUserResponse;
import com.uom.lims.api.common.PageResponse;
import com.uom.lims.api.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * WHY: Defines the contract for the Branch Admin self-service portal. All endpoints
 * are intentionally scoped to the authenticated admin's own branch so that no
 * cross-branch data leakage is possible at the API contract level.
 */
@RequestMapping("/api/v1/branch-management")
@Tag(name = "Branch Management", description = "Branch Admin operations: user management, audit logs, and branch reports")
public interface BranchManagementApi {

    // ------------------------------------------------------------------ Users

    @Operation(summary = "List branch users",
               description = "Returns a paginated list of staff users belonging to the authenticated admin's branch")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<PageResponse<BranchUserResponse>>> getBranchUsers(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "Create a branch user",
               description = "Registers a new staff member under the authenticated admin's branch")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Username or email already exists")
    })
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<BranchUserResponse>> createBranchUser(
            @Valid @RequestBody BranchUserCreateRequest request);

    @Operation(summary = "Update a branch user",
               description = "Updates the profile or role of an existing user within the authenticated admin's branch. Only supplied (non-null) fields are changed.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found in this branch"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or username already taken by another user")
    })
    @PutMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<BranchUserResponse>> updateBranchUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody BranchUserUpdateRequest request);

    // --------------------------------------------------------------- Audit logs

    @Operation(summary = "Get branch audit logs",
               description = "Returns a paginated list of audit log entries scoped to the authenticated admin's branch")
    @GetMapping("/audit-logs")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<PageResponse<BranchAuditLogResponse>>> getBranchAuditLogs(
            @Parameter(description = "Filter by action, e.g. ORDER_CREATED") @RequestParam(required = false) String action,
            @Parameter(description = "Filter by entity type, e.g. ORDER") @RequestParam(required = false) String entityType,
            @Parameter(description = "Filter by the username that performed the action") @RequestParam(required = false) String performedBy,
            @Parameter(description = "Full-text search across patient code, action, and entity type") @RequestParam(required = false) String search,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size);

    // ----------------------------------------------------------------- Reports

    @Operation(summary = "Get branch report",
               description = "Returns aggregate statistics and a summary report for the authenticated admin's branch")
    @GetMapping("/reports")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<BranchReportResponse>> getBranchReport();
}
