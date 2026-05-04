package com.uom.lims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "test_parameters")
@Getter
@Setter
public class TestParameterEntity extends BaseEntity {

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "ref_low", precision = 10, scale = 2)
    private BigDecimal refLow;

    @Column(name = "ref_high", precision = 10, scale = 2)
    private BigDecimal refHigh;

    @Column(name = "display_order")
    private Integer displayOrder;
}