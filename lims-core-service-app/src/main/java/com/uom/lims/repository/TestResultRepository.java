package com.uom.lims.repository;

import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.TestResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResultEntity, UUID> {

    Page<TestResultEntity> findByStatus(ResultStatus status, Pageable pageable);
}