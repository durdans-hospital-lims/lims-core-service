package com.uom.lims.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Immutable
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "patient_code")
    private String patientCode;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "branch_code", nullable = false)
    private String branchCode;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String details;
}
