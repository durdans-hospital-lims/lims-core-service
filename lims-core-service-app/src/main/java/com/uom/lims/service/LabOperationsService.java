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
                        "SYS_XN_1000",
                        "Sysmex XN-1000",
                        "Automated hematology analyzer",
                        "XN-1000",
                        "SYS-2021-4421",
                        "online",
                        "2 mins ago",
                        142,
                        "Hematology Lab - Bench 1",
                        "PASS"),
                new InstrumentStatusResponse(
                        "ROCHE_C311",
                        "Roche cobas c 311",
                        "Clinical chemistry analyzer",
                        "c 311",
                        "ROC-2020-3312",
                        "online",
                        "5 mins ago",
                        98,
                        "Biochemistry Lab - Bench 2",
                        "PASS"),
                new InstrumentStatusResponse(
                        "URINE_STRIP_READER",
                        "Urine strip reader",
                        "Semi-automated urinalysis workstation",
                        "Urisys-class",
                        "URI-2024-0091",
                        "online",
                        "7 mins ago",
                        36,
                        "Urinalysis Bench",
                        "PASS"),
                new InstrumentStatusResponse(
                        "BARCODE_PRINTER",
                        "Thermal barcode label printer",
                        "Sample label printer",
                        "ZD-class",
                        "LBL-2025-5521",
                        "busy",
                        "1 min ago",
                        211,
                        "Collection Desk",
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
                new QcRunItemResponse("qc-001", "Sysmex XN-1000", "Full Blood Count", "Normal", "5.2", "5.0 +/- 0.3", "0.7 SD", "PASS", "MLT Aritha", TIME_FORMATTER.format(now.minusSeconds(5400))),
                new QcRunItemResponse("qc-002", "Sysmex XN-1000", "Full Blood Count", "Low", "2.1", "2.0 +/- 0.2", "0.5 SD", "PASS", "MLT Aritha", TIME_FORMATTER.format(now.minusSeconds(5280))),
                new QcRunItemResponse("qc-003", "Roche cobas c 311", "Lipid Profile", "Normal", "198", "200 +/- 8", "-0.25 SD", "PASS", "MLT Silva", TIME_FORMATTER.format(now.minusSeconds(4500))),
                new QcRunItemResponse("qc-004", "Roche cobas c 311", "HbA1c", "High", "9.8", "8.5 +/- 0.4", "3.25 SD", "WARN", "MLT Silva", TIME_FORMATTER.format(now.minusSeconds(4320))),
                new QcRunItemResponse("qc-005", "Urine strip reader", "Urine Full Report", "Negative Control", "Negative", "Negative", "0 SD", "PASS", "MLT Perera", TIME_FORMATTER.format(now.minusSeconds(3600))),
                new QcRunItemResponse("qc-006", "Urine strip reader", "Urine Full Report", "Positive Control", "Positive", "Positive", "0 SD", "PASS", "MLT Perera", TIME_FORMATTER.format(now.minusSeconds(3300))));
    }
}
