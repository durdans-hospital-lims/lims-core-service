package com.uom.lims.entity;

import com.uom.lims.api.enums.ResultFlag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
}