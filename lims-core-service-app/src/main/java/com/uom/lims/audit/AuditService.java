package com.uom.lims.audit;

import com.uom.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(
            String action,
            String entityType,
            UUID entityId,
            String patientCode,
            String details,
            String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setPatientCode(patientCode);

        // Handle cases where security context might be empty (e.g. system tasks)
        // For legal grade auditing, we might want to throw if user is unknown,
        // but for now we'll allow SYSTEM if context is missing (though our
        // SecurityUtils throws)
        try {
            String username = SecurityUtils.getCurrentUsername();
            auditLog.setPerformedBy(username != null ? username : "SYSTEM");
        } catch (Exception e) {
            auditLog.setPerformedBy("SYSTEM");
        }

        try {
            String branchId = SecurityUtils.getCurrentBranchId();
            auditLog.setBranchCode(branchId != null ? branchId : "SYSTEM");
        } catch (Exception e) {
            auditLog.setBranchCode("UNKNOWN");
        }

        auditLog.setIpAddress(ipAddress);
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog.setDetails(details);

        repository.save(auditLog);
        log.info("Audit log saved: {} on {} by {}", action, entityType, auditLog.getPerformedBy());
    }
}
