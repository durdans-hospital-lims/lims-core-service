package com.uom.lims.instrument;

import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestParameterEntity;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.instrument.astm.AstmMessage;
import com.uom.lims.outbox.OutboxService;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestParameterRepository;
import com.uom.lims.repository.TestResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ingests analyzer results into the LIMS. For each specimen it locates the
 * sample by barcode, maps each device result to a test parameter via LOINC,
 * writes/updates the result (idempotently — verified results are never
 * overwritten) and publishes an INSTRUMENT_RESULT_RECEIVED event through the
 * transactional outbox. Results enter as ENTERED, awaiting verification (Phase 4
 * autoverification will decide auto-release vs. hold).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentResultIngestionService {

    private final SampleRepository sampleRepository;
    private final TestParameterRepository parameterRepository;
    private final TestResultRepository resultRepository;
    private final OutboxService outboxService;

    @Transactional
    public IngestOutcome ingest(AstmMessage.SpecimenResults specimen, String instrumentId) {
        SampleEntity sample = sampleRepository.findByBarcodeAndDeletedFalse(specimen.sampleId()).orElse(null);
        if (sample == null) {
            log.warn("Instrument {}: no sample for barcode {} — {} result(s) skipped",
                    instrumentId, specimen.sampleId(), specimen.results().size());
            return new IngestOutcome(specimen.sampleId(), 0, specimen.results().size());
        }

        UUID testId = sample.getOrderItem().getTestId();
        int ingested = 0;
        int unmatched = 0;

        for (AstmMessage.Result r : specimen.results()) {
            TestParameterEntity parameter = resolveParameter(testId, r.deviceCode());
            if (parameter == null) {
                unmatched++;
                log.warn("Instrument {}: no parameter for device code {} (sample {})",
                        instrumentId, r.deviceCode(), specimen.sampleId());
                continue;
            }

            TestResultEntity result = resultRepository
                    .findBySampleIdAndParameterId(sample.getId(), parameter.getId())
                    .orElseGet(TestResultEntity::new);

            // Never overwrite a result that has already been verified/authorized.
            if (result.getStatus() == ResultStatus.TECHNICALLY_VERIFIED
                    || result.getStatus() == ResultStatus.CLINICALLY_AUTHORIZED) {
                continue;
            }

            BigDecimal numeric = parseNumeric(r.value());
            result.setSample(sample);
            result.setParameter(parameter);
            result.setResultValue(r.value());
            result.setResultNumeric(numeric);
            result.setResultDataType(numeric != null ? "NUMERIC" : "TEXT");
            result.setFlag(mapFlag(r.flag()));
            result.setDraft(false);
            result.setStatus(ResultStatus.ENTERED);
            resultRepository.save(result);
            ingested++;
        }

        if (ingested > 0 && (sample.getStatus() == SampleStatus.ACCEPTED
                || sample.getStatus() == SampleStatus.IN_TESTING)) {
            sample.setStatus(SampleStatus.SENT_FOR_VERIFICATION);
            sampleRepository.save(sample);
        }

        if (ingested > 0) {
            outboxService.append("SAMPLE", specimen.sampleId(), "INSTRUMENT_RESULT_RECEIVED",
                    "lab.instrument.results", specimen.sampleId(),
                    new InstrumentResultEvent(specimen.sampleId(), instrumentId, ingested));
        }

        return new IngestOutcome(specimen.sampleId(), ingested, unmatched);
    }

    private TestParameterEntity resolveParameter(UUID testId, String deviceCode) {
        String loinc = DeviceCodeMap.toLoinc(deviceCode);
        if (loinc == null) {
            return null;
        }
        // Prefer the parameter belonging to the ordered test; fall back to LOINC.
        List<TestParameterEntity> scoped = parameterRepository.findByTestIdAndLoincCode(testId, loinc);
        if (!scoped.isEmpty()) {
            return scoped.get(0);
        }
        List<TestParameterEntity> any = parameterRepository.findByLoincCode(loinc);
        return any.isEmpty() ? null : any.get(0);
    }

    private static ResultFlag mapFlag(String astmFlag) {
        if (astmFlag == null) {
            return ResultFlag.NORMAL;
        }
        return switch (astmFlag.trim().toUpperCase()) {
            case "L" -> ResultFlag.LOW;
            case "H" -> ResultFlag.HIGH;
            case "LL", "<" -> ResultFlag.CRITICAL_LOW;
            case "HH", ">" -> ResultFlag.CRITICAL_HIGH;
            default -> ResultFlag.NORMAL;
        };
    }

    private static BigDecimal parseNumeric(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Outbox payload. */
    public record InstrumentResultEvent(String sampleId, String instrumentId, int resultCount) {
    }

    /** Per-specimen ingestion summary. */
    public record IngestOutcome(String sampleId, int ingested, int unmatched) {
    }
}
