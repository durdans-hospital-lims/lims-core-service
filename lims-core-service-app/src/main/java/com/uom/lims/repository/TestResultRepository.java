package com.uom.lims.repository;

import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.TestResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResultEntity, UUID> {

    List<TestResultEntity> findBySampleId(UUID sampleId);

    List<TestResultEntity> findBySampleIdIn(List<UUID> sampleIds);

    Optional<TestResultEntity> findBySampleIdAndParameterId(UUID sampleId, UUID parameterId);

    Page<TestResultEntity> findByStatusAndDraftFalse(ResultStatus status, Pageable pageable);
}
