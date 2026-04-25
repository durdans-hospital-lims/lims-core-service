package com.uom.lims.api.branch.dto.request;

import com.uom.lims.api.branch.enums.AccountStatus;
import com.uom.lims.api.branch.enums.BranchUserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Payload to update an existing branch user. Only non-null fields are applied.")
public class BranchUserUpdateRequest {

    @Size(max = 150, message = "Full name must not exceed 150 characters")
    @Schema(description = "Updated full name", example = "Kasun Perera")
    private String fullName;

    @Email(message = "Invalid email format")
    @Size(max = 255)
    @Schema(description = "Updated email address", example = "kasun.perera@durdans.lk")
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number format")
    @Schema(description = "Updated phone number", example = "+94771234567")
    private String phone;

    @Schema(description = "Updated account status", example = "SUSPENDED")
    private AccountStatus accountStatus;

    @Schema(description = "Updated role assignment", example = "LAB_SUPERVISOR")
    private BranchUserRole role;
}
