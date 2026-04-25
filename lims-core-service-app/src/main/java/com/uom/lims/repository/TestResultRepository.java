package com.uom.lims.repository;

<<<<<<< HEAD
import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.TestResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
=======
import com.uom.lims.entity.TestResultEntity;
>>>>>>> c310bb40ffe9e73d4a8650c75abb97c3bbc107a4
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResultEntity, UUID> {

<<<<<<< HEAD
    Page<TestResultEntity> findByStatus(ResultStatus status, Pageable pageable);

=======
>>>>>>> c310bb40ffe9e73d4a8650c75abb97c3bbc107a4
    List<TestResultEntity> findBySampleId(UUID sampleId);

    Optional<TestResultEntity> findBySampleIdAndParameterId(UUID sampleId, UUID parameterId);
}