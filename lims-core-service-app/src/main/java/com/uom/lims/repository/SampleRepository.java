package com.uom.lims.repository;

import com.uom.lims.api.enums.Priority;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.entity.SampleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WHY: Isolates database access for phlebotomy workflows allowing dynamic
 * queueing and chain-of-custody tracking.
 */
@Repository
public interface SampleRepository extends JpaRepository<SampleEntity, UUID>, JpaSpecificationExecutor<SampleEntity> {
    Optional<SampleEntity> findByBarcodeAndDeletedFalse(String barcode);

    boolean existsByBarcodeAndDeletedFalse(String barcode);

    Page<SampleEntity> findAllByStatusAndDeletedFalse(SampleStatus status, Pageable pageable);

    // WHY: Collection history combines COLLECTED and REJECTED — IN clause avoids
    // two separate queries.
    Page<SampleEntity> findAllByStatusInAndDeletedFalse(List<SampleStatus> statuses, Pageable pageable);

    long countByStatusAndDeletedFalse(SampleStatus status);

    long countByStatusAndCollectedAtBetweenAndDeletedFalse(SampleStatus status, Instant start, Instant end);

    // WHY: Urgent count uses a DB-level filter on priority list — avoids loading
    // all pending samples into memory.
    long countByStatusAndPriorityInAndDeletedFalse(SampleStatus status, List<Priority> priorities);

    // WHY: Today's rejection count uses rejectedAt timestamp bounds to scope to
    // current shift/day only.
    long countByStatusAndRejectedAtBetweenAndDeletedFalse(SampleStatus status, Instant start, Instant end);

    List<SampleEntity> findByStatusInAndDeletedFalseOrderByCollectedAtAsc(List<SampleStatus> statuses);
}
