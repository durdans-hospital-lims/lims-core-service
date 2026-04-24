package com.uom.lims.controller;

import com.uom.lims.api.dto.request.SampleRejectRequest;
import com.uom.lims.api.dto.response.MltWorklistItemResponse;
import com.uom.lims.service.MltTestingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reception/samples")
@RequiredArgsConstructor
public class AccessioningController {

    private final MltTestingService mltTestingService;
    
    @GetMapping
    public ResponseEntity<List<MltWorklistItemResponse>> getCollectedSamples() {
        return ResponseEntity.ok(mltTestingService.getCollectedSamples());
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Void> acceptSample(@PathVariable UUID id) {
        mltTestingService.acceptSample(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectSample(
            @PathVariable UUID id,
            @Valid @RequestBody SampleRejectRequest request) {
        mltTestingService.rejectSample(id, request);
        return ResponseEntity.ok().build();
    }
}