package com.uom.lims.api.clinical.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnToMLTRequest {
    @NotNull
    private String resultId;

    @NotNull
    private String status;

    private String returnReason;
}