package com.uom.lims.service;

import com.uom.lims.api.dto.response.InstrumentStatusResponse;
import com.uom.lims.api.dto.response.QcDashboardResponse;
import com.uom.lims.api.dto.response.QcRunItemResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class LabOperationsService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a")
            .withZone(ZoneId.systemDefault());

    public QcDashboardResponse getQcDashboard() {
        List<QcRunItemResponse> runs = buildQcRuns();

        int passed = (int) runs.stream().filter(run -> "PASS".equals(run.status())).count();
        int warnings = (int) runs.stream().filter(run -> "WARN".equals(run.status())).count();
        int failures = (int) runs.stream().filter(run -> "FAIL".equals(run.status())).count();

        return new QcDashboardResponse(
                runs.size(),
                passed,
                warnings,
                failures,
                runs);
    }

    public List<InstrumentStatusResponse> getInstruments() {
        return List.of(
                new InstrumentStatusResponse(
                        "inst-001",
                        "Sysmex XN-1000",
                        "Haematology Analyser",
                        "XN-1000",
                        "SYS-2021-4421",
                        "online",
                        "2 mins ago",
                        142,
                        "Haematology Lab — Bench 1",
                        "PASS"),
                new InstrumentStatusResponse(
                        "inst-002",
                        "Cobas c501",
                        "Chemistry Analyser",
                        "c501",
                        "COB-2020-3312",
                        "online",
                        "5 mins ago",
                        98,
                        "Biochemistry Lab — Bench 2",
                        "FAIL"),
                new InstrumentStatusResponse(
                        "inst-003",
                        "BioMérieux VITEK 2",
                        "Microbiology ID/AST",
                        "VITEK 2",
                        "VIT-2022-0091",
                        "offline",
                        "45 mins ago",
                        12,
                        "Microbiology Lab — Bench 4",
                        "PASS"),
                new InstrumentStatusResponse(
                        "inst-004",
                        "Cobas e411",
                        "Immunoassay Analyser",
                        "e411",
                        "COB-2019-5521",
                        "busy",
                        "1 min ago",
                        64,
                        "Immunology Lab — Bench 3",
                        "WARN"));
    }

    public InstrumentStatusResponse syncInstrument(String instrumentId) {
        return getInstruments().stream()
                .filter(instrument -> instrument.id().equals(instrumentId))
                .findFirst()
                .map(instrument -> new InstrumentStatusResponse(
                        instrument.id(),
                        instrument.name(),
                        instrument.type(),
                        instrument.model(),
                        instrument.serial(),
                        instrument.status(),
                        "Just now",
                        instrument.testsToday(),
                        instrument.location(),
                        instrument.qcStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + instrumentId));
    }

    private List<QcRunItemResponse> buildQcRuns() {
        Instant now = Instant.now();

        return List.of(
                new QcRunItemResponse("qc-001", "Sysmex XN-1000", "Full Blood Count", "Normal", "5.2", "5.0 ± 0.3", "0.7 SD", "PASS", "MLT Aritha", TIME_FORMATTER.format(now.minusSeconds(5400))),
                new QcRunItemResponse("qc-002", "Sysmex XN-1000", "Full Blood Count", "Low", "2.1", "2.0 ± 0.2", "0.5 SD", "PASS", "MLT Aritha", TIME_FORMATTER.format(now.minusSeconds(5280))),
                new QcRunItemResponse("qc-003", "Cobas c501", "Lipid Panel", "Normal", "198", "200 ± 8", "-0.25 SD", "PASS", "MLT Silva", TIME_FORMATTER.format(now.minusSeconds(4500))),
                new QcRunItemResponse("qc-004", "Cobas c501", "HbA1c", "High", "9.8", "8.5 ± 0.4", "3.25 SD", "FAIL", "MLT Silva", TIME_FORMATTER.format(now.minusSeconds(4320))),
                new QcRunItemResponse("qc-005", "Cobas e411", "Thyroid Panel", "Normal", "4.1", "4.0 ± 0.5", "0.2 SD", "PASS", "MLT Perera", TIME_FORMATTER.format(now.minusSeconds(3600))),
                new QcRunItemResponse("qc-006", "Cobas e411", "Cortisol", "Low", "3.8", "5.0 ± 0.8", "-1.5 SD", "WARN", "MLT Perera", TIME_FORMATTER.format(now.minusSeconds(3300))));
    }
}
