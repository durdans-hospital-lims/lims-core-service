package com.uom.lims.repository;

import com.uom.lims.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * WHY: Manages access to granular payment interactions to enforce correct financial auditing and reversal flows.
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    List<PaymentEntity> findAllByBillIdAndDeletedFalse(UUID billId);
    List<PaymentEntity> findAllByBillIdAndReversedFalseAndDeletedFalse(UUID billId);
}
