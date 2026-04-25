package com.uom.lims.entity;

<<<<<<< HEAD
import com.uom.lims.api.verification.enums.ResultStatus;
=======
import com.uom.lims.api.enums.ResultFlag;
>>>>>>> c310bb40ffe9e73d4a8650c75abb97c3bbc107a4
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
<<<<<<< HEAD
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

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
=======
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "test_results")
@Getter
@Setter
public class TestResultEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sample_id", nullable = false)
    private SampleEntity sample;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parameter_id", nullable = false)
    private TestParameterEntity parameter;

    @Column(name = "result_value", nullable = false, length = 100)
    private String resultValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag", length = 30)
    private ResultFlag flag;

    @Column(name = "mlt_notes", length = 500)
    private String mltNotes;

    @Column(name = "is_draft", nullable = false)
    private Boolean draft = true;
>>>>>>> c310bb40ffe9e73d4a8650c75abb97c3bbc107a4
}