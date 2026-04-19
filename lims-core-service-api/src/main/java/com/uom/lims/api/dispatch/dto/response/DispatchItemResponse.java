package com.uom.lims.api.dispatch.dto.response;

import com.uom.lims.api.dispatch.enums.DeliveryMethod;
import com.uom.lims.api.dispatch.enums.DispatchItemStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authorized report in the dispatch queue with delivery attempts")
public class DispatchItemResponse {

    private UUID id;
    private String reportReference;
    private String branchCode;
    private String patientCode;
    private String patientDisplayName;
    private String testPanelLabel;
    private String artifactUri;
    private OffsetDateTime authorizedAt;
    private DispatchItemStatus overallStatus;
    private List<DeliveryMethod> preferredDeliveryMethods;
    private List<DeliveryAttemptResponse> attempts;
}
