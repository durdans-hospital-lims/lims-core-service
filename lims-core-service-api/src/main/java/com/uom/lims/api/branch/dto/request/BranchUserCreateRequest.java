package com.uom.lims.api.branch.dto.request;

import com.uom.lims.api.branch.enums.AccountStatus;
import com.uom.lims.api.branch.enums.BranchUserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Payload to create a new user scoped to the Branch Admin's branch")
public class BranchUserCreateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    @Schema(description = "Full name of the staff member", example = "Kasun Perera", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    @Schema(description = "Email address", example = "kasun.perera@durdans.lk", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number format")
    @Schema(description = "Contact phone number", example = "+94771234567", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, dots, underscores, or hyphens")
    @Schema(description = "Unique login username", example = "kasun.perera", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotNull(message = "Account status is required")
    @Schema(description = "Initial account status", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private AccountStatus accountStatus;

    @NotNull(message = "Role assignment is required")
    @Schema(description = "Role assigned to the user within this branch", example = "MLT", requiredMode = Schema.RequiredMode.REQUIRED)
    private BranchUserRole role;
}
