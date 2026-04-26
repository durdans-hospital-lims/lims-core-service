package com.uom.lims.api.verification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResultDetailResponse {
    private String resultId;
    private String status;
    private String patientName;
    private Instant createdAt;
    private Instant updatedAt;
    private String technicianName;
    private String pathologistName;
    private List<TestResultParameterResponse> parameters;
    private String clinicalNote;
    private String mltNotes;
}