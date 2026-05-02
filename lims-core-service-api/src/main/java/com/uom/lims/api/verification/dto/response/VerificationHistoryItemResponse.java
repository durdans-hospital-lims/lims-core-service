package com.uom.lims.api.verification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationHistoryItemResponse {
    private String resultId;
    private String actionType;
    private String testName;
    private String actionSummary;
    private String performedBy;
    private Instant actionAt;
    private String notes;
    private Instant updatedAt;
}
