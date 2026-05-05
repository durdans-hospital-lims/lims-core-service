package com.uom.lims.service;

import com.uom.lims.api.dto.request.SampleCollectRequest;
import com.uom.lims.api.dto.request.SampleRejectRequest;
import com.uom.lims.api.dto.response.CollectionHistoryResponse;
import com.uom.lims.api.dto.response.SamplePatientInfo;
import com.uom.lims.api.dto.response.SampleResponse;
import com.uom.lims.api.enums.PaymentStatus;
import com.uom.lims.api.enums.RejectionReason;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.api.patient.dto.response.PatientResponse;
import com.uom.lims.entity.BillEntity;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestCatalogEntity;
import com.uom.lims.exception.BusinessValidationException;
import com.uom.lims.exception.InvalidStateTransitionException;
import com.uom.lims.exception.ResourceNotFoundException;
import com.uom.lims.repository.BillRepository;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestCatalogRepository;
import com.uom.lims.util.ReferenceNumberGenerator;
import com.uom.lims.util.SecurityUtils;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/**
 * WHY: Manages the physical specimen collection workflow — the bridge between the
 * clinical order and the laboratory analysis. Every state change (collection, rejection,
 * recollection) is audit-logged with the phlebotomist's identity and timestamp to
 * satisfy medicolegal chain-of-custody requirements for clinical specimens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SampleService {

    private final SampleRepository sampleRepository;
    private final BillRepository billRepository;
    private final TestCatalogRepository testCatalogRepository;
    private final PatientClientService patientClientService;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final SecurityUtils securityUtils;

    /**
     * WHY: The phlebotomy worklist only shows samples awaiting initial collection or
     * recollection — collected and rejected samples are separated into history to keep
     * the active queue short and action-focused for the phlebotomist.
     *
     * @param pageable pagination parameters from the request
     * @return paginated page of SampleResponse DTOs in PENDING_COLLECTION status
     */
    @Transactional(readOnly = true)
    public Page<SampleResponse> getPendingSamples(Pageable pageable) {
        return sampleRepository.findAllByStatusAndDeletedFalse(SampleStatus.PENDING_COLLECTION, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SampleResponse> searchSamplesForReprint(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();

        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<SampleEntity> specification = (root, criteriaQuery, criteriaBuilder) -> {
            var orderItemJoin = root.join("orderItem", JoinType.INNER);
            var orderJoin = orderItemJoin.join("order", JoinType.INNER);

            var testJoin = criteriaQuery.from(TestCatalogEntity.class);
            var testLink = criteriaBuilder.equal(testJoin.get("id"), orderItemJoin.get("testId"));

            String likeQuery = "%" + normalizedQuery + "%";

            var barcodePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), likeQuery);
            var patientPredicate = criteriaBuilder.like(criteriaBuilder.lower(orderJoin.get("patientId")), likeQuery);
            var orderPredicate = criteriaBuilder.like(criteriaBuilder.lower(orderJoin.get("orderNo")), likeQuery);
            var testNamePredicate = criteriaBuilder.like(criteriaBuilder.lower(testJoin.get("testName")), likeQuery);
            var testCodePredicate = criteriaBuilder.like(criteriaBuilder.lower(testJoin.get("testCode")), likeQuery);
            var notDeletedPredicate = criteriaBuilder.isFalse(root.get("deleted"));
            var notRejectedPredicate = criteriaBuilder.notEqual(root.get("status"), SampleStatus.REJECTED);

            criteriaQuery.distinct(true);

            return criteriaBuilder.and(
                    notDeletedPredicate,
                    notRejectedPredicate,
                    testLink,
                    criteriaBuilder.or(
                            barcodePredicate,
                            patientPredicate,
                            orderPredicate,
                            testNamePredicate,
                            testCodePredicate));
        };

        return sampleRepository.findAll(specification, pageable)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * WHY: Collection history is a separate view from the active queue — supervisors
     * and quality managers review completed and rejected samples for throughput analysis.
     * Combining both COLLECTED and REJECTED in history gives a complete audit trail.
     *
     * @param pageable pagination parameters from the request
     * @return paginated page of CollectionHistoryResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<CollectionHistoryResponse> getCollectionHistory(Pageable pageable) {
        // WHY: We query COLLECTED first — REJECTED samples are included separately.
        // Using findAll with status filter and mapping both statuses as history is the
        // simplest approach given the current repository contract.
        Page<SampleEntity> history = sampleRepository
                .findAllByStatusInAndDeletedFalse(
                        List.of(SampleStatus.COLLECTED, SampleStatus.REJECTED), pageable);
        return history.map(this::toHistoryResponse);
    }

    /**
     * WHY: Collection is gated behind bill payment validation for clinical revenue
     * assurance — samples are only processed once the patient's financial commitment
     * is confirmed. This prevents samples from being collected for orders that
     * will never be paid, which wastes consumables and analyst time.
     *
     * Samples in RECOLLECTION_REQUIRED status are also allowed through because
     * rejection already generated a new sample record for this purpose.
     *
     * @param sampleId the UUID of the sample to collect
     * @param request  optional phlebotomist notes from the collection event
     * @return the updated SampleResponse DTO
     * @throws ResourceNotFoundException       if the sample does not exist
     * @throws InvalidStateTransitionException if the sample is not in a collectable state
     * @throws BusinessValidationException     if the bill is not paid
     */
    public SampleResponse collectSample(UUID sampleId, SampleCollectRequest request) {
        SampleEntity sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sample not found with id: " + sampleId));

        if (sample.getStatus() != SampleStatus.PENDING_COLLECTION
                && sample.getStatus() != SampleStatus.RECOLLECTION_REQUIRED) {
            throw new InvalidStateTransitionException(
                    "Cannot collect sample " + sample.getBarcode() +
                            " — current status is " + sample.getStatus() +
                            ". Only PENDING_COLLECTION or RECOLLECTION_REQUIRED samples can be collected.");
        }

        // WHY: Bill payment gate — fetch the bill through the order item → order chain.
        // Checking payment before processing protects laboratory resources.
        UUID orderId = sample.getOrderItem().getOrder().getId();
        BillEntity bill = billRepository.findByOrderIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No bill found for order linked to sample " + sample.getBarcode()));

        if (bill.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessValidationException(
                    "Cannot collect sample " + sample.getBarcode() +
                            " — associated bill " + bill.getBillNo() +
                            " is not fully paid. Current payment status: " + bill.getPaymentStatus());
        }

        sample.setStatus(SampleStatus.COLLECTED);
        sample.setCollectedAt(Instant.now());
        sample.setCollectedBy(securityUtils.getCurrentUsername());

        SampleEntity saved = sampleRepository.save(sample);
        log.info("Sample {} collected by {}", saved.getBarcode(), saved.getCollectedBy());
        return toResponse(saved);
    }

    /**
     * WHY: Sample rejection triggers immediate recollection request generation.
     * Creating the replacement sample record atomically in the same transaction
     * ensures the phlebotomy queue always reflects the correct pending count —
     * rejected samples are never left without a follow-up recollection request.
     *
     * RejectionReason.OTHER requires explicit free-text notes for the quality
     * management system — vague 'other' rejections without explanation are
     * disallowed to maintain IQCP compliance.
     *
     * @param sampleId the UUID of the sample to reject
     * @param request  rejection reason and mandatory notes if reason is OTHER
     * @return the SampleResponse DTO of the newly created recollection sample
     * @throws ResourceNotFoundException       if the sample does not exist
     * @throws InvalidStateTransitionException if the sample cannot be rejected from its state
     * @throws BusinessValidationException     if reason is OTHER but notes are blank
     */
    public SampleResponse rejectSample(UUID sampleId, SampleRejectRequest request) {
        SampleEntity sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sample not found with id: " + sampleId));

        if (sample.getStatus() != SampleStatus.PENDING_COLLECTION
                && sample.getStatus() != SampleStatus.COLLECTED) {
            throw new InvalidStateTransitionException(
                    "Cannot reject sample " + sample.getBarcode() +
                            " — current status is " + sample.getStatus() +
                            ". Only PENDING_COLLECTION or COLLECTED samples can be rejected.");
        }

        // WHY: Free-text justification is mandatory for 'OTHER' to prevent
        // rejection records that carry no actionable quality improvement information.
        if (request.getRejectionReason() == RejectionReason.OTHER) {
            if (request.getRejectionNotes() == null || request.getRejectionNotes().isBlank()) {
                throw new BusinessValidationException(
                        "Rejection notes are mandatory when rejection reason is OTHER");
            }
        }

        // Mark the original sample as REJECTED.
        sample.setStatus(SampleStatus.REJECTED);
        sample.setRejectedAt(Instant.now());
        sample.setRejectedBy(securityUtils.getCurrentUsername());
        sample.setRejectionReason(request.getRejectionReason());
        sample.setRejectionNotes(request.getRejectionNotes());
        SampleEntity rejectedSample = sampleRepository.save(sample);
        log.info("Sample {} rejected by {} for reason: {}",
                sample.getBarcode(), sample.getRejectedBy(), sample.getRejectionReason());

        // Create a new sample for recollection — inheriting tube type, priority,
        // and order item from the rejected sample.
        SampleEntity recollection = new SampleEntity();
        recollection.setOrderItem(rejectedSample.getOrderItem());
        recollection.setBarcode(referenceNumberGenerator.generateBarcode());
        recollection.setTubeType(rejectedSample.getTubeType());
        recollection.setPriority(rejectedSample.getPriority());
        recollection.setStatus(SampleStatus.RECOLLECTION_REQUIRED);
        recollection.setParentSample(rejectedSample);
        recollection.setRecollectionCount(rejectedSample.getRecollectionCount() + 1);
        recollection.setCreatedBy(securityUtils.getCurrentUsername());

        SampleEntity savedRecollection = sampleRepository.save(recollection);
        log.info("Recollection sample {} created for rejected sample {}",
                savedRecollection.getBarcode(), rejectedSample.getBarcode());

        return toResponse(savedRecollection);
    }

    /**
     * WHY: Label reprints must be traceable. The browser handles the physical print
     * dialog, while the service records each approved print attempt against the
     * specimen before the label is rendered.
     */
    public SampleResponse printSampleLabel(UUID sampleId) {
        SampleEntity sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sample not found with id: " + sampleId));

        sample.setPrintCount((sample.getPrintCount() == null ? 0 : sample.getPrintCount()) + 1);
        SampleEntity saved = sampleRepository.save(sample);
        log.info("Sample {} label print count incremented to {}", saved.getBarcode(), saved.getPrintCount());
        return toResponse(saved);
    }

    /**
     * WHY: Centralized entity-to-DTO mapping keeps all field assignments in one
     * place. The patient info sub-object is populated with the patientId only —
     * full patient details must be fetched from the Patient service to avoid
     * cross-service data duplication (patient demographic ownership belongs to
     * the Patient module, not Order/Sample).
     *
     * @param sample the SampleEntity to convert
     * @return the SampleResponse DTO safe for exposure outside the service layer
     */
    private SampleResponse toResponse(SampleEntity sample) {
        String patientId = sample.getOrderItem() != null && sample.getOrderItem().getOrder() != null
                ? sample.getOrderItem().getOrder().getPatientId()
                : null;
        PatientResponse patient = getPatientSafely(patientId);

        SamplePatientInfo patientInfo = SamplePatientInfo.builder()
                .pid(patientId)
                .name(patient != null ? patient.getFullName() : null)
                .age(patient != null && patient.getDob() != null
                        ? Period.between(patient.getDob(), LocalDate.now()).getYears()
                        : null)
                .gender(patient != null && patient.getGender() != null
                        ? patient.getGender().name()
                        : null)
                .build();

        TestCatalogEntity test = testCatalogRepository
                .findById(sample.getOrderItem().getTestId()).orElse(null);

        return SampleResponse.builder()
                .id(sample.getId())
                .sampleId(sample.getBarcode())
                .orderId(sample.getOrderItem() != null && sample.getOrderItem().getOrder() != null
                        ? sample.getOrderItem().getOrder().getOrderNo()
                        : null)
                .testType(test != null ? test.getTestName() : null)
                .testCodes(test != null ? List.of(test.getTestCode()) : null)
                .priority(sample.getPriority())
                .tubeTypes(List.of(sample.getTubeType()))
                .waitTimeMinutes(sample.getCreatedAt() != null ?
                        java.time.temporal.ChronoUnit.MINUTES.between(
                                sample.getCreatedAt(), Instant.now()) : 0)
                .status(sample.getStatus())
                .patient(patientInfo)
                .collectedAt(sample.getCollectedAt())
                .collectedBy(sample.getCollectedBy())
                .printCount(sample.getPrintCount() != null ? sample.getPrintCount() : 0)
                .rejectionReason(sample.getRejectionReason())
                .rejectionNotes(sample.getRejectionNotes())
                .build();
    }

    /**
     * WHY: History responses use a reduced DTO shape optimised for the supervisor
     * audit table — fewer fields than the worklist view to keep the payload compact.
     *
     * @param sample the SampleEntity to convert to history format
     * @return the CollectionHistoryResponse DTO
     */
    private CollectionHistoryResponse toHistoryResponse(SampleEntity sample) {
        String patientId = (sample.getOrderItem() != null && sample.getOrderItem().getOrder() != null)
                ? sample.getOrderItem().getOrder().getPatientId()
                : null;

        PatientResponse patient = getPatientSafely(patientId);

        TestCatalogEntity test = testCatalogRepository
                .findById(sample.getOrderItem().getTestId()).orElse(null);
        Instant eventTime = sample.getCollectedAt() != null ? sample.getCollectedAt() : sample.getRejectedAt();
        String eventBy = sample.getCollectedBy() != null ? sample.getCollectedBy() : sample.getRejectedBy();
        long waitTime = sample.getCreatedAt() != null && eventTime != null
                ? java.time.temporal.ChronoUnit.MINUTES.between(sample.getCreatedAt(), eventTime)
                : 0;

        return CollectionHistoryResponse.builder()
                .id(sample.getId())
                .sampleId(sample.getBarcode())
                .patientName(patient != null ? patient.getFullName() : null)
                .pid(patientId)
                .testCodes(test != null ? List.of(test.getTestCode()) : List.of())
                .tubeType(sample.getTubeType())
                .priority(sample.getPriority())
                .status(sample.getStatus())
                .collectedAt(eventTime)
                .collectedBy(eventBy)
                .printCount(sample.getPrintCount() != null ? sample.getPrintCount() : 0)
                .waitTime(waitTime)
                .rejectionNotes(sample.getRejectionNotes())
                .build();
    }

    private PatientResponse getPatientSafely(String patientId) {
        if (patientId == null) {
            return null;
        }

        try {
            return patientClientService.getPatientByCode(patientId, securityUtils.getCurrentBearerToken());
        } catch (Exception e) {
            log.warn("Unable to enrich sample response with patient {} details", patientId, e);
            return null;
        }
    }
}
