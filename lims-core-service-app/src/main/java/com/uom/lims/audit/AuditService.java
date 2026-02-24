package com.uom.lims.audit;

import com.uom.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /**
     * Query audit logs with optional filters.
     * If branchCode is provided, only logs for that branch are returned.
     * If branchCode is null, all logs are returned (for SUPER_ADMIN).
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(
            String branchCode,
            String action,
            String entityType,
            String performedBy,
            String search,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        // Normalize empty strings to null for the JPA query
        action = (action != null && action.isBlank()) ? null : action;
        entityType = (entityType != null && entityType.isBlank()) ? null : entityType;
        performedBy = (performedBy != null && performedBy.isBlank()) ? null : performedBy;
        search = (search != null && search.isBlank()) ? null : search;

        if (branchCode != null && !branchCode.isBlank()) {
            return repository.findByBranchCodeFiltered(branchCode, action, entityType, performedBy, search, pageable);
        } else {
            return repository.findAllFiltered(action, entityType, performedBy, search, pageable);
        }
    }
}
