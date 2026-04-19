package com.uom.lims.dispatch;

import com.uom.lims.api.dispatch.dto.request.DispatchReportRequest;
import com.uom.lims.api.dispatch.enums.DeliveryAttemptStatus;
import com.uom.lims.notification.EmailService;
import com.uom.lims.notification.SmsService;
import com.uom.lims.patient.PatientEntity;
import com.uom.lims.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportDispatchChannelService {

    private final EmailService emailService;
    private final SmsService smsService;
    private final PatientRepository patientRepository;

    public void executeChannel(
            ReportDispatchItemEntity item,
            ReportDeliveryAttemptEntity attempt,
            DispatchReportRequest request) {

        LocalDateTime now = LocalDateTime.now();
        attempt.setDispatchedAt(now);

        switch (attempt.getMethod()) {
            case PRINT -> {
                attempt.setStatus(DeliveryAttemptStatus.DELIVERED);
                attempt.setDeliveredAt(now);
                attempt.setFailureReason(null);
            }
            case EMAIL -> {
                String email = firstNonBlank(request.getOverrideEmail(),
                        resolvePatientEmail(item.getPatientCode()));
                if (email == null) {
                    attempt.setStatus(DeliveryAttemptStatus.FAILED);
                    attempt.setFailureReason("NO_EMAIL: Patient has no email and no override was provided");
                    return;
                }
                try {
                    emailService.sendLabReportEmail(
                            email,
                            item.getPatientDisplayName(),
                            item.getReportReference(),
                            item.getTestPanelLabel(),
                            item.getArtifactUri());
                    attempt.setStatus(DeliveryAttemptStatus.DELIVERED);
                    attempt.setDeliveredAt(LocalDateTime.now());
                    attempt.setFailureReason(null);
                } catch (RuntimeException ex) {
                    attempt.setStatus(DeliveryAttemptStatus.FAILED);
                    attempt.setFailureReason("EMAIL_SEND_FAILED: " + truncate(ex.getMessage(), 900));
                }
            }
            case SMS -> {
                String phone = firstNonBlank(request.getOverridePhone(),
                        resolvePatientPhone(item.getPatientCode()));
                if (phone == null) {
                    attempt.setStatus(DeliveryAttemptStatus.FAILED);
                    attempt.setFailureReason("NO_PHONE: Patient has no phone and no override was provided");
                    return;
                }
                try {
                    String msg = "Durdans Lab: Report " + item.getReportReference() + " is ready. "
                            + (item.getArtifactUri() != null && !item.getArtifactUri().isBlank()
                                    ? "Link: " + item.getArtifactUri()
                                    : "Collect from laboratory.");
                    smsService.sendSms(phone, msg);
                    attempt.setStatus(DeliveryAttemptStatus.DELIVERED);
                    attempt.setDeliveredAt(LocalDateTime.now());
                    attempt.setFailureReason(null);
                } catch (RuntimeException ex) {
                    attempt.setStatus(DeliveryAttemptStatus.FAILED);
                    attempt.setFailureReason("SMS_SEND_FAILED: " + truncate(ex.getMessage(), 900));
                }
            }
            case PORTAL -> {
                attempt.setStatus(DeliveryAttemptStatus.DELIVERED);
                attempt.setDeliveredAt(now);
                attempt.setFailureReason(null);
            }
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private String resolvePatientEmail(String patientCode) {
        if (patientCode == null || patientCode.isBlank()) {
            return null;
        }
        Optional<PatientEntity> p = patientRepository.findByPatientCode(patientCode.trim());
        return p.map(PatientEntity::getEmail).filter(e -> e != null && !e.isBlank()).orElse(null);
    }

    private String resolvePatientPhone(String patientCode) {
        if (patientCode == null || patientCode.isBlank()) {
            return null;
        }
        Optional<PatientEntity> p = patientRepository.findByPatientCode(patientCode.trim());
        if (p.isEmpty()) {
            return null;
        }
        PatientEntity pe = p.get();
        if (pe.getPhone() != null && !pe.getPhone().isBlank()) {
            return pe.getPhone().trim();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
