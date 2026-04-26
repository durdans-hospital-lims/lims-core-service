package com.uom.lims.controller;

import com.uom.lims.api.dto.response.VerificationPendingItemResponse;
import com.uom.lims.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @GetMapping("/pending")
    public ResponseEntity<List<VerificationPendingItemResponse>> getPendingSamples() {
        return ResponseEntity.ok(verificationService.getPendingSamples());
    }
}