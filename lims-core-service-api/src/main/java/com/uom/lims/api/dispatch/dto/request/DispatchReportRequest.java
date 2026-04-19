package com.uom.lims.api.dispatch.dto.request;

import com.uom.lims.api.dispatch.enums.DeliveryMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Execute dispatch for selected channels")
public class DispatchReportRequest {

    @NotEmpty
    @Schema(description = "Channels to dispatch now", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DeliveryMethod> methods;

    @Schema(description = "Override patient email when different from registration")
    private String overrideEmail;

    @Schema(description = "Override patient phone (E.164 or local digits) for SMS")
    private String overridePhone;
}
