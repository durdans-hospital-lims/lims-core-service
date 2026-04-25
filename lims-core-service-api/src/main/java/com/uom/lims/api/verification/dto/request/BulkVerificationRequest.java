package com.uom.lims.api.verification.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkVerificationRequest {
    private List<String> resultIds;
    private String status;
    private String mltNotes;
}