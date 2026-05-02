package com.uom.lims.controller;

import com.uom.lims.api.dto.response.InstrumentStatusResponse;
import com.uom.lims.api.dto.response.QcDashboardResponse;
import com.uom.lims.service.LabOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mlt")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MLT','LAB_SUPERVISOR','BRANCH_ADMIN','SUPER_ADMIN')")
public class MltOperationsController {

    private final LabOperationsService labOperationsService;

    @GetMapping("/qc-dashboard")
    public ResponseEntity<QcDashboardResponse> getQcDashboard() {
        return ResponseEntity.ok(labOperationsService.getQcDashboard());
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<InstrumentStatusResponse>> getInstruments() {
        return ResponseEntity.ok(labOperationsService.getInstruments());
    }

    @PostMapping("/instruments/{id}/sync")
    public ResponseEntity<InstrumentStatusResponse> syncInstrument(@PathVariable String id) {
        return ResponseEntity.ok(labOperationsService.syncInstrument(id));
    }
}
