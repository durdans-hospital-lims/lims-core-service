package com.uom.lims.api.branch.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "A single audit log entry scoped to the authenticated admin's branch")
public class BranchAuditLogResponse {

    @Schema(description = "Unique identifier of the audit log entry")
    private String id;

    @Schema(description = "Action that was performed, e.g. ORDER_CREATED", example = "ORDER_CREATED")
    private String action;

    @Schema(description = "Type of entity the action was performed on, e.g. ORDER", example = "ORDER")
    private String entityType;

    @Schema(description = "Identifier of the affected entity")
    private String entityId;

    @Schema(description = "Patient code related to the action, if applicable")
    private String patientCode;

    @Schema(description = "Username of the person who performed the action")
    private String performedBy;

    @Schema(description = "Branch code where the action occurred")
    private String branchCode;

    @Schema(description = "IP address from which the action was performed")
    private String ipAddress;

    @Schema(description = "ISO-8601 timestamp of when the action occurred")
    private String timestamp;

    @Schema(description = "JSON details payload with additional context for the action")
    private String details;
}
