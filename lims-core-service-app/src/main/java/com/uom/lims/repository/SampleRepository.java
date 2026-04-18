package com.uom.lims.repository;

import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.entity.SampleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * WHY: Isolates database access for phlebotomy workflows allowing dynamic queueing and chain-of-custody tracking.
 */
@Repository
public interface SampleRepository extends JpaRepository<SampleEntity, UUID>, JpaSpecificationExecutor<SampleEntity> {
    Optional<SampleEntity> findByBarcodeAndDeletedFalse(String barcode);
    boolean existsByBarcodeAndDeletedFalse(String barcode);
    Page<SampleEntity> findAllByStatusAndDeletedFalse(SampleStatus status, Pageable pageable);
    long countByStatusAndDeletedFalse(SampleStatus status);
    long countByStatusAndCollectedAtBetweenAndDeletedFalse(SampleStatus status, Instant start, Instant end);
}
