package com.uom.lims.service;

import com.uom.lims.api.dto.request.BillDiscountRequest;
import com.uom.lims.api.dto.request.PaymentRequest;
import com.uom.lims.api.dto.response.BillResponse;
import com.uom.lims.api.dto.response.OrderItemResponse;
import com.uom.lims.api.dto.response.PaymentResponse;
import com.uom.lims.api.enums.PaymentMethod;
import com.uom.lims.api.enums.PaymentStatus;
import com.uom.lims.config.BillingProperties;
import com.uom.lims.entity.BillEntity;
import com.uom.lims.entity.PaymentEntity;
import com.uom.lims.exception.BusinessValidationException;
import com.uom.lims.exception.ResourceNotFoundException;
import com.uom.lims.repository.BillRepository;
import com.uom.lims.repository.OrderRepository;
import com.uom.lims.repository.PaymentRepository;
import com.uom.lims.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * WHY: Manages the complete financial lifecycle of a patient invoice including
 * discount application, payment processing, and automated overdue detection.
 * Separating billing from clinical order logic means finance rules can evolve
 * independently of sample collection workflows — a key separation of concerns
 * required by hospital accounting regulations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BillingService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final BillingProperties billingProperties;
    private final SecurityUtils securityUtils;

    /**
     * WHY: Fetching a bill by order ID is the primary path from the order detail page —
     * the frontend navigates order → bill rather than storing bill IDs directly.
     *
     * @param orderId the UUID of the parent order
     * @return the corresponding BillResponse DTO
     * @throws ResourceNotFoundException if no bill exists for the order
     */
    @Transactional(readOnly = true)
    public BillResponse getBillByOrderId(UUID orderId) {
        BillEntity bill = billRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found for order id: " + orderId));
        return toResponse(bill);
    }

    /**
     * WHY: Direct bill lookup by ID is used by the payment and print workflows
     * which receive the bill UUID from prior API responses.
     *
     * @param billId the UUID of the bill
     * @return the corresponding BillResponse DTO
     * @throws ResourceNotFoundException if the bill does not exist
     */
    @Transactional(readOnly = true)
    public BillResponse getBillById(UUID billId) {
        BillEntity bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));
        return toResponse(bill);
    }

    /**
     * WHY: Discounts are applied by authorised cashiers before payment. Validating
     * that the discount never exceeds totalAmount prevents the system from creating
     * a negative balance — which would corrupt accounting reconciliation.
     * Blocking discounts on already-PAID bills prevents retroactive adjustments
     * that would invalidate issued receipts.
     *
     * @param billId  the UUID of the bill to discount
     * @param request the discount amount and mandatory audit reason
     * @return the updated BillResponse DTO
     * @throws ResourceNotFoundException  if the bill does not exist
     * @throws BusinessValidationException if the bill is already PAID or discount exceeds total
     */
    public BillResponse applyDiscount(UUID billId, BillDiscountRequest request) {
        BillEntity bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessValidationException(
                    "Cannot apply discount to bill " + bill.getBillNo() + " — bill is already fully paid");
        }

        if (request.getDiscountAmount().compareTo(bill.getTotalAmount()) > 0) {
            throw new BusinessValidationException(
                    "Discount " + request.getDiscountAmount() +
                            " exceeds total amount " + bill.getTotalAmount() + " for bill " + bill.getBillNo());
        }

        bill.setDiscount(request.getDiscountAmount());
        // WHY: totalAmount is always recalculated from source values (subtotal + serviceCharge - discount)
        // rather than subtracting from the existing totalAmount to prevent compounding errors
        // if applyDiscount is called multiple times.
        bill.setTotalAmount(bill.getSubtotal().add(bill.getServiceCharge()).subtract(request.getDiscountAmount()));

        BillEntity saved = billRepository.save(bill);
        log.info("Discount {} applied to bill {} by {}", request.getDiscountAmount(),
                bill.getBillNo(), securityUtils.getCurrentUsername());
        return toResponse(saved);
    }

    /**
     * WHY: Payment processing validates payment amount against outstanding balance,
     * enforces payment-method-specific reference requirements, and automatically
     * transitions billing status — eliminating the need for manual status management.
     * Using BigDecimal.compareTo() (not equals()) is critical for monetary comparison
     * because BigDecimal("5.0").equals(BigDecimal("5.00")) returns false.
     *
     * @param billId  the UUID of the bill being paid
     * @param request the payment details including amount, method, and references
     * @return the updated BillResponse DTO with recalculated payment status
     * @throws ResourceNotFoundException  if the bill does not exist
     * @throws BusinessValidationException for any payment rule violation
     */
    public BillResponse processPayment(UUID billId, PaymentRequest request) {
        BillEntity bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessValidationException(
                    "Bill " + bill.getBillNo() + " is already fully paid — no further payments accepted");
        }

        BigDecimal outstanding = bill.getTotalAmount().subtract(bill.getPaidAmount());
        if (request.getAmount().compareTo(outstanding) > 0) {
            throw new BusinessValidationException(
                    "Payment amount " + request.getAmount() +
                            " exceeds outstanding balance " + outstanding + " for bill " + bill.getBillNo());
        }

        // WHY: Bank transfers require a reference number for banking reconciliation.
        // Insurance requires a claim number for insurer reimbursement tracking.
        // Failing here prevents untracked payments that cannot be reconciled later.
        if (request.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            if (request.getBankReferenceNo() == null || request.getBankReferenceNo().isBlank()) {
                throw new BusinessValidationException(
                        "Bank reference number is required for BANK_TRANSFER payments");
            }
        }
        if (request.getPaymentMethod() == PaymentMethod.INSURANCE) {
            if (request.getInsuranceClaimNo() == null || request.getInsuranceClaimNo().isBlank()) {
                throw new BusinessValidationException(
                        "Insurance claim number is required for INSURANCE payments");
            }
        }

        // Build and persist the payment transaction record.
        PaymentEntity payment = new PaymentEntity();
        payment.setBill(bill);
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setBankReferenceNo(request.getBankReferenceNo());
        payment.setBankName(request.getBankName());
        payment.setInsuranceClaimNo(request.getInsuranceClaimNo());
        payment.setPaymentDate(Instant.now());
        payment.setReversed(false);
        paymentRepository.save(payment);

        // Update paidAmount and derive new payment status.
        BigDecimal newPaidAmount = bill.getPaidAmount().add(request.getAmount());
        bill.setPaidAmount(newPaidAmount);

        // WHY: Using compareTo(0) instead of >= to correctly handle BigDecimal scale differences.
        if (newPaidAmount.compareTo(bill.getTotalAmount()) >= 0) {
            bill.setPaymentStatus(PaymentStatus.PAID);
        } else {
            bill.setPaymentStatus(PaymentStatus.PARTIAL);
        }

        BillEntity saved = billRepository.save(bill);
        log.info("Payment {} processed for bill {} — new status: {} by {}",
                request.getAmount(), bill.getBillNo(), saved.getPaymentStatus(),
                securityUtils.getCurrentUsername());
        return toResponse(saved);
    }

    /**
     * WHY: Recording print events supports audit requirements — hospital policy
     * requires tracking when and by whom each invoice was issued to a patient.
     * The print count supports re-print detection for fraud prevention.
     *
     * @param billId the UUID of the bill being printed
     * @return the updated BillResponse DTO with refreshed print metadata
     * @throws ResourceNotFoundException if the bill does not exist
     */
    public BillResponse recordBillPrint(UUID billId) {
        BillEntity bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found with id: " + billId));

        bill.setPrintCount(bill.getPrintCount() + 1);
        bill.setLastPrintedAt(Instant.now());
        bill.setLastPrintedBy(securityUtils.getCurrentUsername());

        BillEntity saved = billRepository.save(bill);
        log.info("Bill {} printed (count: {}) by {}", bill.getBillNo(),
                saved.getPrintCount(), saved.getLastPrintedBy());
        return toResponse(saved);
    }

    /**
     * WHY: Overdue detection runs hourly so that finance reports reflect accurate
     * payment status without requiring a manual refresh. Running every 3,600,000 ms
     * (1 hour) balances accuracy against database load.
     * The threshold is read from BillingProperties — not hardcoded — so finance
     * can adjust the overdue window via configuration without a code release.
     */
    @Scheduled(fixedRate = 3_600_000)
    public void markOverdueBills() {
        Instant overdueThreshold = Instant.now()
                .minus(billingProperties.getOverdueThresholdHours(), ChronoUnit.HOURS);

        List<BillEntity> pendingBills = billRepository
                .findAllByPaymentStatusAndDeletedFalse(PaymentStatus.PENDING, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        int overdueCount = 0;
        for (BillEntity bill : pendingBills) {
            if (bill.getCreatedAt() != null && bill.getCreatedAt().isBefore(overdueThreshold)) {
                bill.setPaymentStatus(PaymentStatus.OVERDUE);
                billRepository.save(bill);
                overdueCount++;
            }
        }
        if (overdueCount > 0) {
            log.info("Marked {} bills as OVERDUE (threshold: {} hours)",
                    overdueCount, billingProperties.getOverdueThresholdHours());
        }
    }

    /**
     * WHY: All entity-to-DTO mapping is centralised here so that every method
     * in this service returns an identical, consistent BillResponse shape.
     * outstandingAmount is computed here (not stored) to avoid data inconsistency
     * between the DB columns and the derived value.
     *
     * @param bill the BillEntity to convert
     * @return the BillResponse DTO safe for exposure outside the service layer
     */
    private BillResponse toResponse(BillEntity bill) {
        // Map order items to line-item responses if the order is loaded.
        List<OrderItemResponse> itemResponses = bill.getOrder() != null && bill.getOrder().getItems() != null
                ? bill.getOrder().getItems().stream()
                        .map(item -> OrderItemResponse.builder()
                                .testId(item.getTestId().toString())
                                .price(item.getPrice())
                                .build())
                        .toList()
                : List.of();

        // Map payment history for the receipt view.
        List<PaymentResponse> paymentResponses = bill.getPayments() != null
                ? bill.getPayments().stream()
                        .map(p -> PaymentResponse.builder()
                                .id(p.getId())
                                .amount(p.getAmount())
                                .paymentMethod(p.getPaymentMethod())
                                .paymentDate(p.getPaymentDate())
                                .reversed(p.isReversed())
                                .build())
                        .toList()
                : List.of();

        // Compute outstanding amount in the mapping layer — never stored in DB.
        BigDecimal outstanding = bill.getTotalAmount().subtract(bill.getPaidAmount());

        return BillResponse.builder()
                .id(bill.getId())
                .billId(bill.getBillNo())
                .orderId(bill.getOrder() != null ? bill.getOrder().getOrderNo() : null)
                .patientId(bill.getOrder() != null ? bill.getOrder().getPatientId() : null)
                .orderDate(bill.getOrder() != null ? bill.getOrder().getCreatedAt() : null)
                .billDate(bill.getCreatedAt())
                .subtotal(bill.getSubtotal())
                .serviceCharge(bill.getServiceCharge())
                .discount(bill.getDiscount())
                .totalAmount(bill.getTotalAmount())
                .paidAmount(bill.getPaidAmount())
                .outstandingAmount(outstanding)
                .paymentStatus(bill.getPaymentStatus())
                .tests(itemResponses)
                .payments(paymentResponses)
                .build();
    }
}
