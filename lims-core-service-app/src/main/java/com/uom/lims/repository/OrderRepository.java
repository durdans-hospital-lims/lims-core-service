package com.uom.lims.repository;

import com.uom.lims.api.enums.OrderStatus;
import com.uom.lims.entity.OrderEntity;
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
 * WHY: Provides data access operations for managing patient orders and retrieving aggregated statistics.
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID>, JpaSpecificationExecutor<OrderEntity> {
    Optional<OrderEntity> findByOrderNoAndDeletedFalse(String orderNo);
    Optional<OrderEntity> findByIdAndDeletedFalse(UUID id);
    Page<OrderEntity> findAllByDeletedFalse(Pageable pageable);
    boolean existsByPatientIdAndStatusInAndDeletedFalse(String patientId, List<OrderStatus> statuses);
    long countByCreatedAtBetweenAndDeletedFalse(Instant start, Instant end);
}
