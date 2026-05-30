package com.uom.lims.compliance;

import com.uom.lims.audit.AuditService;
import com.uom.lims.exception.ResourceNotFoundException;
import com.uom.lims.patient.PatientEntity;
import com.uom.lims.patient.PatientRepository;
import com.uom.lims.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sri Lanka PDPA data-subject-request handling: consent capture, the right of
 * access (data export) and the right to erasure (anonymisation). Health data is
 * a special category, so these operations are audited and erasure de-identifies
 * the record while retaining the clinical results (linked by the anonymised id)
 * for the legally-required retention period.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSubjectRequestService {

    private final PatientRepository patientRepository;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    /** Record the patient's consent to process their health data. */
    @Transactional
    public void recordConsent(String patientCode, String consentVersion, String ipAddress) {
        PatientEntity patient = require(patientCode);
        patient.setConsentGiven(true);
        patient.setConsentAt(LocalDateTime.now());
        patient.setConsentVersion(consentVersion);
        patientRepository.save(patient);
        auditService.log("PDPA_CONSENT_RECORDED", "PATIENT", patient.getId(), patientCode,
                String.format("{\"version\":\"%s\"}", consentVersion), ipAddress);
    }

    /** Right of access: assemble the data held about the patient. */
    @Transactional(readOnly = true)
    public PatientDataExport export(String patientCode, String ipAddress) {
        PatientEntity p = require(patientCode);
        long orderCount = orderRepository.countByPatientIdAndDeletedFalse(patientCode);
        auditService.log("PDPA_DATA_EXPORT", "PATIENT", p.getId(), patientCode, "{}", ipAddress);
        return new PatientDataExport(
                p.getPatientCode(), p.getFullName(), p.getDob(), p.getGender() == null ? null : p.getGender().name(),
                p.getEmail(), p.getPhone(), p.getIdentityNumber(), p.getAddress(), p.getBranchCode(),
                p.isConsentGiven(), p.getConsentVersion(), orderCount);
    }

    /**
     * Right to erasure: de-identify the patient record. Unique not-null columns
     * (phone) get a stable anonymised placeholder; the rest of the PII is cleared.
     * Clinical results remain linked to the (now anonymised) patient for retention.
     */
    @Transactional
    public void erase(String patientCode, String reason, String ipAddress) {
        PatientEntity p = require(patientCode);
        if (p.isAnonymized()) {
            return;
        }
        p.setFullName("REDACTED");
        p.setEmail(null);
        p.setPhone("ANON-" + p.getPatientCode());
        p.setIdentityNumber(null);
        p.setIdentityType(null);
        p.setAddress("REDACTED");
        p.setHomeNumber(null);
        p.setContactPersonName(null);
        p.setContactPersonPhone(null);
        p.setProfilePhotoPath(null);
        // De-identify DOB to birth year only.
        if (p.getDob() != null) {
            p.setDob(LocalDate.of(p.getDob().getYear(), 1, 1));
        }
        p.setAnonymized(true);
        p.setDeleted(true);
        patientRepository.save(p);
        auditService.log("PDPA_ERASURE", "PATIENT", p.getId(), patientCode,
                String.format("{\"reason\":\"%s\"}", reason == null ? "" : reason), ipAddress);
        log.info("Patient {} anonymised under right-to-erasure", patientCode);
    }

    private PatientEntity require(String patientCode) {
        return patientRepository.findByPatientCode(patientCode)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientCode));
    }

    /** Access-right export payload. */
    public record PatientDataExport(String patientCode, String fullName, LocalDate dob, String gender,
                                    String email, String phone, String identityNumber, String address,
                                    String branchCode, boolean consentGiven, String consentVersion,
                                    long orderCount) {
    }
}
