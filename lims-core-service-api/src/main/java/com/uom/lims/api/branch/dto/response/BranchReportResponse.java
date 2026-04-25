package com.uom.lims.api.branch.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Aggregate operational report for a branch")
public class BranchReportResponse {

    @Schema(description = "Branch code", example = "COL-1")
    private String branchCode;

    @Schema(description = "Human-readable branch name", example = "Colombo 01 Branch")
    private String branchName;

    @Schema(description = "Total registered users in this branch")
    private long totalUsers;

    @Schema(description = "Number of ACTIVE users in this branch")
    private long activeUsers;

    @Schema(description = "Number of INACTIVE users in this branch")
    private long inactiveUsers;

    @Schema(description = "Number of SUSPENDED users in this branch")
    private long suspendedUsers;

    @Schema(description = "Total audit log entries recorded for this branch")
    private long totalAuditLogs;
}
