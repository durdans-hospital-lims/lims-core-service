package com.uom.lims.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;
import com.uom.lims.api.verification.enums.ParameterFlag;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "test_result_parameters")
@Where(clause = "is_deleted = false")
public class TestResultParameterEntity extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "test_result_id", referencedColumnName = "id")
    private TestResultEntity testResult;

    @Column(name = "parameter_code", nullable = false)
    private String parameterCode;

    @Column(name = "parameter_name", nullable = false)
    private String parameterName;

    @Column(name = "result_value")
    private BigDecimal resultValue;

    @Column(name = "result_text")
    private String resultText;

    @Column(name = "unit")
    private String unit;

    @Column(name = "reference_range_low")
    private BigDecimal referenceRangeLow;

    @Column(name = "reference_range_high")
    private BigDecimal referenceRangeHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag")
    private ParameterFlag flag;

    @Builder.Default
    @Column(name = "is_abnormal", nullable = false)
    private Boolean isAbnormal = false;

    @Column(name = "sort_order")
    private Integer sortOrder;
}