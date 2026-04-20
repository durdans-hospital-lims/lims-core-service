package com.uom.lims.service;

import com.uom.lims.api.dto.request.OrderCreateRequest;
import com.uom.lims.api.dto.response.OrderItemResponse;
import com.uom.lims.api.dto.response.OrderResponse;
import com.uom.lims.api.enums.OrderStatus;
import com.uom.lims.api.enums.PaymentStatus;
import com.uom.lims.api.enums.Priority;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.config.BillingProperties;
import com.uom.lims.entity.BillEntity;
import com.uom.lims.entity.OrderEntity;
import com.uom.lims.entity.OrderItemEntity;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestCatalogEntity;
import com.uom.lims.exception.DuplicateResourceException;
import com.uom.lims.exception.InvalidStateTransitionException;
import com.uom.lims.exception.ResourceNotFoundException;
import com.uom.lims.repository.BillRepository;
import com.uom.lims.repository.OrderRepository;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestCatalogRepository;
import com.uom.lims.util.ReferenceNumberGenerator;
import com.uom.lims.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * WHY: Orchestrates the full lifecycle of a clinical order from test selection through
 * billing and sample creation. Keeping this orchestration in one service ensures
 * that order, bill, and sample are always created atomically — no partial records
 * can exist in the database if any step fails (@Transactional rolls back all).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final TestCatalogRepository testCatalogRepository;
    private final BillRepository billRepository;
    private final SampleRepository sampleRepository;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final SecurityUtils securityUtils;
    private final BillingProperties billingProperties;

    /**
     * WHY: Creating an order is the entry point for the entire clinical workflow.
     * The method is intentionally structured as sequential numbered steps matching
     * the clinical process so future maintainers can trace each domain action clearly.
     * All 9 steps run atomically — a billing failure rolls back the order record too.
     *
     * @param request the order creation payload from the receptionist
     * @return the created order mapped to a response DTO
     */
    public OrderResponse createOrder(OrderCreateRequest request) {
        log.info("Creating order for patient {} by user {}", request.getPatientId(), securityUtils.getCurrentUsername());

        // Step 1: Resolve test catalog entries — fail fast if any testId is invalid or inactive.
        // WHY: Using findAllByIdInAndActiveTrueAndDeletedFalse avoids separate per-test queries.
        // A Map is built keyed by ID so items can be enriched without parallel-index assumptions.
        List<UUID> testUUIDs = request.getTestIds().stream()
                .map(UUID::fromString)
                .toList();

        List<TestCatalogEntity> tests = testCatalogRepository.findAllByIdInAndActiveTrueAndDeletedFalse(testUUIDs);

        if (tests.size() != testUUIDs.size()) {
            throw new ResourceNotFoundException(
                    "One or more test IDs not found, inactive, or deleted in the catalog");
        }

        // WHY: Map by UUID so order item enrichment (Step 9 toResponse) is O(1) lookup
        // rather than a nested O(n²) stream scan.
        Map<UUID, TestCatalogEntity> testMap = tests.stream()
                .collect(Collectors.toMap(TestCatalogEntity::getId, Function.identity()));

        // Step 2: Prevent duplicate active orders for the same patient.
        // WHY: Two active orders simultaneously create ambiguous sample routing and duplicate billing.
        boolean duplicateExists = orderRepository.existsByPatientIdAndStatusInAndDeletedFalse(
                request.getPatientId(), List.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS));
        if (duplicateExists) {
            throw new DuplicateResourceException(
                    "Patient " + request.getPatientId() + " already has an active order in PENDING or IN_PROGRESS state");
        }

        // Step 3: Build the order aggregate root.
        OrderEntity order = new OrderEntity();
        order.setOrderNo(referenceNumberGenerator.generateOrderNo());
        order.setPatientId(request.getPatientId());
        order.setPriority(request.getPriority() != null ? request.getPriority() : Priority.NORMAL);
        order.setReferringDoctor(request.getReferringDoctor());
        order.setReferringDepartment(request.getReferringDepartment());
        order.setRemarks(request.getRemarks());
        order.setStatus(OrderStatus.PENDING);

        // Step 4: Build one OrderItemEntity per test, snapshotting the catalog price.
        List<OrderItemEntity> items = new ArrayList<>();
        for (TestCatalogEntity test : tests) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrder(order);
            item.setTestId(test.getId());
            item.setPrice(test.getPrice());
            item.setStatus(SampleStatus.PENDING_COLLECTION);
            items.add(item);
        }
        order.setItems(items);

        // Step 5: Persist the order with its items cascade.
        OrderEntity savedOrder = orderRepository.save(order);

        // Step 6: Calculate billing amounts using BigDecimal to avoid floating-point errors.
        // WHY: RoundingMode.HALF_UP is standard for monetary values (matches bank rounding).
        BigDecimal subtotal = items.stream()
                .map(OrderItemEntity::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal serviceChargeRate = billingProperties.getServiceChargePercentage()
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal serviceCharge = subtotal.multiply(serviceChargeRate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(serviceCharge);

        // Step 7: Create the bill linked to the order.
        BillEntity bill = new BillEntity();
        bill.setOrder(savedOrder);
        bill.setBillNo(referenceNumberGenerator.generateBillNo());
        bill.setSubtotal(subtotal);
        bill.setServiceCharge(serviceCharge);
        bill.setDiscount(BigDecimal.ZERO);
        bill.setTotalAmount(totalAmount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setPaymentStatus(PaymentStatus.PENDING);
        billRepository.save(bill);

        // Step 8: Create one SampleEntity per order item with a unique barcode.
        // WHY: Each test may require a different tube type — separate sample records
        // allow the phlebotomist to label and track each tube independently.
        // Using testMap lookup (not parallel index) prevents mismatched item-to-test assignments.
        for (OrderItemEntity item : savedOrder.getItems()) {
            TestCatalogEntity test = testMap.get(item.getTestId());
            SampleEntity sample = new SampleEntity();
            sample.setOrderItem(item);
            sample.setBarcode(referenceNumberGenerator.generateBarcode());
            sample.setTubeType(test.getTubeType());
            sample.setPriority(savedOrder.getPriority());
            sample.setStatus(SampleStatus.PENDING_COLLECTION);
            sample.setRecollectionCount(0);
            sampleRepository.save(sample);
        }

        log.info("Order {} created successfully with bill for patient {}",
                savedOrder.getOrderNo(), savedOrder.getPatientId());

        // Step 9: Map the saved entity to a response DTO, enriching items with test catalog data.
        return toResponse(savedOrder, testMap);
    }

    /**
     * WHY: Paginated retrieval with deletedFalse filter prevents soft-deleted orders
     * from appearing in administrative lists — controllers never receive raw entity lists.
     *
     * @param pageable pagination and sorting parameters from the request
     * @return paginated page of OrderResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Pageable pageable) {
        // WHY: Spring Data JPA derivation — findAllByDeletedFalse scopes out soft-deleted records.
        return orderRepository.findAllByDeletedFalse(pageable)
                .map(o -> toResponse(o, null));
    }

    /**
     * WHY: Lookup by UUID is the primary retrieval path. Using UUID prevents sequential ID enumeration.
     *
     * @param id the internal UUID of the order
     * @return the matching order as a response DTO
     * @throws ResourceNotFoundException if the order does not exist
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        OrderEntity order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return toResponse(order, null);
    }

    /**
     * WHY: Only PENDING orders can be cancelled — cancelling an IN_PROGRESS order
     * would abandon samples already in transit to the laboratory, violating chain of custody.
     *
     * @param id the internal UUID of the order to cancel
     * @return the updated order as a response DTO
     * @throws ResourceNotFoundException       if the order does not exist
     * @throws InvalidStateTransitionException if the order is not in PENDING state
     */
    public OrderResponse cancelOrder(UUID id) {
        OrderEntity order = orderRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    "Cannot cancel order " + order.getOrderNo() +
                            " — only PENDING orders can be cancelled, current status is: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        OrderEntity saved = orderRepository.save(order);
        log.info("Order {} cancelled by {}", saved.getOrderNo(), securityUtils.getCurrentUsername());
        return toResponse(saved, null);
    }

    /**
     * WHY: All entity-to-DTO mapping is centralised here. The testMap parameter allows
     * item lines to be enriched with testCode/testName from the catalog. When testMap is
     * null (read-only paths where catalog was not pre-fetched) only price and testId are mapped —
     * keeping read paths fast without a second catalog query.
     *
     * @param order   the OrderEntity to convert
     * @param testMap optional map of UUID → TestCatalogEntity for item enrichment, may be null
     * @return the OrderResponse DTO safe for exposure outside the service layer
     */
    private OrderResponse toResponse(OrderEntity order, Map<UUID, TestCatalogEntity> testMap) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> {
                    TestCatalogEntity test = testMap != null ? testMap.get(item.getTestId()) : null;
                    return OrderItemResponse.builder()
                            .testId(item.getTestId().toString())
                            .testCode(test != null ? test.getTestCode() : null)
                            .testName(test != null ? test.getTestName() : null)
                            .category(test != null ? test.getCategory() : null)
                            .price(item.getPrice())
                            .build();
                })
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderId(order.getOrderNo())
                .patientId(order.getPatientId())
                .orderDate(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .status(order.getStatus())
                .priority(order.getPriority())
                .referringDoctor(order.getReferringDoctor())
                .referringDepartment(order.getReferringDepartment())
                .remarks(order.getRemarks())
                .tests(itemResponses)
                .build();
    }
}
