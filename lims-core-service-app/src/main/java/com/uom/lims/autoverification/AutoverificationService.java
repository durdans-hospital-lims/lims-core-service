package com.uom.lims.autoverification;

import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.entity.TestResultEntity;
import org.springframework.stereotype.Service;

/**
 * Autoverification policy (CLSI AUTO10/AUTO15 style). Conservative by design:
 * only a normal, numeric, in-range result is eligible for automatic release;
 * anything abnormal, critical or non-numeric is held for a human verifier.
 *
 * <p>This is the decision point the instrument-ingestion path consults so that
 * routine normal results flow through without manual entry while exceptions are
 * surfaced for review (review-by-exception).
 */
@Service
public class AutoverificationService {

    public record Decision(boolean autoVerify, String reason) {
    }

    public Decision decide(TestResultEntity result) {
        ResultFlag flag = result.getFlag();

        if (flag == ResultFlag.CRITICAL_LOW || flag == ResultFlag.CRITICAL_HIGH) {
            return new Decision(false, "Critical value — requires manual review and notification");
        }
        if (flag == ResultFlag.LOW || flag == ResultFlag.HIGH) {
            return new Decision(false, "Abnormal result — held for manual verification");
        }
        if (result.getResultNumeric() == null) {
            return new Decision(false, "Non-numeric result — held for manual review");
        }
        // A delta check against the patient's prior result is the next rule to add
        // here once historical results are queryable per patient+parameter.
        return new Decision(true, "Normal numeric result within reference range — auto-released");
    }
}
