package com.uom.lims.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;
import com.uom.lims.api.verification.enums.ResultStatus;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "test_results")
@Where(clause = "is_deleted = false")
public class TestResultEntity extends BaseEntity {
    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ResultStatus status;

    @Column(name = "mlt_notes")
    private String mltNotes;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "clinical_note")
    private String clinicalNote;

    @Column(name = "technically_verified_by")
    private String technicallyVerifiedBy;

    @Column(name = "technically_verified_at")
    private Instant technicallyVerifiedAt;

    @Column(name = "clinically_authorized_by")
    private String clinicallyAuthorizedBy;

    @Column(name = "clinically_authorized_at")
    private Instant clinicallyAuthorizedAt;

    @Column(name = "returned_by")
    private String returnedBy;

    @Column(name = "returned_at")
    private Instant returnedAt;

    // Alias methods for updated_at and updated_by (maps to JPA audit fields)
    public Instant getUpdatedAt() {
        return this.getLastModifiedAt();
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.setLastModifiedAt(updatedAt);
    }

    public String getUpdatedBy() {
        return this.getLastModifiedBy();
    }

    public void setUpdatedBy(String updatedBy) {
        this.setLastModifiedBy(updatedBy);
    }
}