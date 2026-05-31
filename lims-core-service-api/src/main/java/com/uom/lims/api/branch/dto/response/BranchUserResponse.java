package com.uom.lims.api.branch.dto.response;

import com.uom.lims.api.branch.enums.AccountStatus;
import com.uom.lims.api.branch.enums.BranchUserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Details of a user scoped to a branch")
public class BranchUserResponse {

    @Schema(description = "Unique identifier of the branch user record")
    private UUID id;

    @Schema(description = "Full name of the staff member", example = "Kasun Perera")
    private String fullName;

    @Schema(description = "Email address", example = "kasun.perera@durdans.lk")
    private String email;

    @Schema(description = "Phone number", example = "+94771234567")
    private String phone;

    @Schema(description = "Login username", example = "kasun.perera")
    private String username;

    @Schema(description = "Account status", example = "ACTIVE")
    private AccountStatus accountStatus;

    @Schema(description = "Assigned role within the branch", example = "MLT")
    private BranchUserRole role;

    @Schema(description = "Branch code this user belongs to", example = "COL-1")
    private String branchCode;

    @Schema(description = "Timestamp when this user record was created")
    private Instant createdAt;

    @Schema(description = "Timestamp of the last update to this user record")
    private Instant updatedAt;
}
