package com.uom.lims.api.dto.response;

import java.util.List;
import java.util.UUID;

public record SampleResultsResponse(
        UUID sampleId,
        String barcode,
        UUID orderId,
        UUID orderItemId,
        String patientName,
        String testName,
        String status,
        List<ResultParameterResponse> results,
        String mltNotes) {
}