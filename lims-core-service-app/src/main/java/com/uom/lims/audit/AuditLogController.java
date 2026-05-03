package com.uom.lims.audit;

import com.uom.lims.api.common.PageResponse;
import com.uom.lims.security.ClientIpResolver;
import com.uom.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;

    public static final String ENTITY_REVENUE_REPORT = "REVENUE_REPORT";
    public static final String ACTION_REVENUE_REPORT_VIEWED = "REVENUE_REPORT_VIEWED";
    public static final String ACTION_REVENUE_REPORT_EXPORTED = "REVENUE_REPORT_EXPORTED";

    @PreAuthorize("hasAnyRole('FRONT_DESK','BRANCH_ADMIN','SUPER_ADMIN')")
    @GetMapping
    public PageResponse<AuditLogResponse> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

            // Normalize empty strings to null
            action = normalizeParam(action);
            entityType = normalizeParam(entityType);
            performedBy = normalizeParam(performedBy);
            search = normalizeParam(search);

            Page<AuditLog> result;

            if (isSuperAdmin()) {
                log.info("Super admin requesting all audit logs");
                result = auditLogRepository.findAllFiltered(action, entityType, performedBy, search, pageable);
            } else {
                String branchCode = SecurityUtils.getCurrentBranchId();
                log.info("User requesting audit logs for branch: {}", branchCode);
                if (branchCode == null || branchCode.isBlank()) {
                    // If branch code is not available, return empty
                    return new PageResponse<>(List.of(), page, size, 0, 0, true);
                }
                result = auditLogRepository.findByBranchCodeFiltered(branchCode, action, entityType, performedBy,
                        search, pageable);
            }

            List<AuditLogResponse> responses = result.getContent().stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());

            return new PageResponse<>(
                    responses,
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages(),
                    result.isLast());

        } catch (Exception e) {
            log.error("Error fetching audit logs", e);
            throw e;
        }
    }

    /**
     * Records revenue report screen access. {@code performedBy} is resolved from the security context.
     */
    @PreAuthorize("hasAnyRole('FRONT_DESK','BRANCH_ADMIN','SUPER_ADMIN')")
    @PostMapping("/revenue-report-access")
    public ResponseEntity<Void> recordRevenueReportAccess(
            @RequestBody(required = false) RevenueReportAccessRequest body,
            HttpServletRequest request) {
        String event = body != null && body.getEvent() != null ? body.getEvent().trim().toUpperCase() : "VIEW";
        String action = "EXPORT".equals(event) ? ACTION_REVENUE_REPORT_EXPORTED : ACTION_REVENUE_REPORT_VIEWED;
        String details = body != null ? body.getDetail() : null;
        auditService.writeStandalone(action, ENTITY_REVENUE_REPORT, null, null, details,
                ClientIpResolver.resolve(request));
        return ResponseEntity.noContent().build();
    }

    private String normalizeParam(String param) {
        return (param != null && !param.isBlank()) ? param.trim() : null;
    }

    private boolean isSuperAdmin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null)
                return false;
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_SUPER_ADMIN"));
        } catch (Exception e) {
            log.warn("Could not determine super admin status", e);
            return false;
        }
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        AuditLogResponse r = new AuditLogResponse();
        r.setId(auditLog.getId() != null ? auditLog.getId().toString() : "");
        r.setAction(auditLog.getAction());
        r.setEntityType(auditLog.getEntityType());
        r.setEntityId(auditLog.getEntityId() != null ? auditLog.getEntityId().toString() : null);
        r.setPatientCode(auditLog.getPatientCode());
        r.setPerformedBy(auditLog.getPerformedBy());
        r.setBranchCode(auditLog.getBranchCode());
        r.setIpAddress(auditLog.getIpAddress());
        r.setTimestamp(auditLog.getTimestamp() != null ? auditLog.getTimestamp().toString() : "");
        r.setDetails(auditLog.getDetails());
        return r;
    }
}
