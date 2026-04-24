package com.uom.lims.controller;

import com.uom.lims.api.dto.request.SubmitResultsRequest;
import jakarta.validation.Valid;
import com.uom.lims.api.dto.response.SampleResultsResponse;
import com.uom.lims.service.MltTestingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mlt")
@RequiredArgsConstructor
public class MltTestingController {

    private final MltTestingService mltTestingService;

    @PostMapping("/samples/{id}/results")
    public ResponseEntity<Void> submitResults(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitResultsRequest request) {
        mltTestingService.submitResults(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/samples/{id}/results")
    public ResponseEntity<SampleResultsResponse> getSampleResults(@PathVariable UUID id) {
        return ResponseEntity.ok(mltTestingService.getSampleResults(id));
    }
}