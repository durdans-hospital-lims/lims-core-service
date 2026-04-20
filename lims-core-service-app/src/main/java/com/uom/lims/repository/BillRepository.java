package com.uom.lims.repository;

import com.uom.lims.api.enums.PaymentStatus;
import com.uom.lims.entity.BillEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * WHY: Manages the financial aggregations of patients enabling invoice querying and payment state updates.
 */
@Repository
public interface BillRepository extends JpaRepository<BillEntity, UUID> {
    Optional<BillEntity> findByOrderIdAndDeletedFalse(UUID orderId);
    Optional<BillEntity> findByBillNoAndDeletedFalse(String billNo);
    long countByPaymentStatusAndDeletedFalse(PaymentStatus status);
    Page<BillEntity> findAllByPaymentStatusAndDeletedFalse(PaymentStatus status, Pageable pageable);
}
