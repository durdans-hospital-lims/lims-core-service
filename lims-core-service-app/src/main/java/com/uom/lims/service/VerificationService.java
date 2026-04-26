package com.uom.lims.service;

import com.uom.lims.api.dto.response.VerificationPendingItemResponse;
import com.uom.lims.api.enums.ResultFlag;
import com.uom.lims.api.enums.SampleStatus;
import com.uom.lims.entity.SampleEntity;
import com.uom.lims.entity.TestCatalogEntity;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.patient.PatientEntity;
import com.uom.lims.patient.PatientRepository;
import com.uom.lims.repository.SampleRepository;
import com.uom.lims.repository.TestCatalogRepository;
import com.uom.lims.repository.TestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VerificationService {

        private final SampleRepository sampleRepository;
        private final TestCatalogRepository testCatalogRepository;
        private final TestResultRepository testResultRepository;
        private final PatientRepository patientRepository;

        @Transactional(readOnly = true)
        public List<VerificationPendingItemResponse> getPendingSamples() {
                List<SampleEntity> samples = sampleRepository.findByStatusInAndDeletedFalseOrderByCollectedAtAsc(
                                List.of(SampleStatus.SENT_FOR_VERIFICATION));

                List<UUID> testIds = samples.stream()
                                .map(sample -> sample.getOrderItem().getTestId())
                                .distinct()
                                .toList();

                Map<UUID, String> testNameById = testCatalogRepository.findAllById(testIds).stream()
                                .collect(Collectors.toMap(
                                                TestCatalogEntity::getId,
                                                TestCatalogEntity::getTestName,
                                                (existing, replacement) -> existing));

                List<UUID> sampleIds = samples.stream()
                                .map(SampleEntity::getId)
                                .toList();

                Map<UUID, List<TestResultEntity>> resultsBySampleId = testResultRepository.findBySampleIdIn(sampleIds)
                                .stream()
                                .collect(Collectors.groupingBy(result -> result.getSample().getId()));

                Map<String, String> patientNameByCode = samples.stream()
                                .map(sample -> sample.getOrderItem().getOrder().getPatientId())
                                .distinct()
                                .collect(Collectors.toMap(
                                                Function.identity(),
                                                patientCode -> patientRepository.findByPatientCode(patientCode)
                                                                .map(PatientEntity::getFullName)
                                                                .orElse("UNKNOWN_PATIENT")));

                return samples.stream()
                                .map(sample -> {
                                        List<TestResultEntity> results = resultsBySampleId.getOrDefault(sample.getId(),
                                                        List.of());
                                        TestResultEntity latestResult = results.stream()
                                                        .max(Comparator.comparing(
                                                                        result -> result.getLastModifiedAt() != null
                                                                                        ? result.getLastModifiedAt()
                                                                                        : result.getCreatedAt()))
                                                        .orElse(null);

                                        ResultFlag overallFlag = results.stream()
                                                        .map(TestResultEntity::getFlag)
                                                        .filter(flag -> flag != null)
                                                        .max(Comparator.comparingInt(this::flagSeverity))
                                                        .orElse(null);

                                        return new VerificationPendingItemResponse(
                                                        sample.getId(),
                                                        sample.getBarcode(),
                                                        sample.getOrderItem().getOrder().getId(),
                                                        sample.getOrderItem().getOrder().getPatientId(),
                                                        patientNameByCode.getOrDefault(
                                                                        sample.getOrderItem().getOrder().getPatientId(),
                                                                        "UNKNOWN_PATIENT"),
                                                        testNameById.getOrDefault(sample.getOrderItem().getTestId(),
                                                                        "UNKNOWN_TEST"),
                                                        sample.getPriority().name(),
                                                        sample.getStatus().name(),
                                                        overallFlag != null ? overallFlag.name() : null,
                                                        latestResult != null
                                                                        ? (latestResult.getLastModifiedBy() != null
                                                                                        ? latestResult.getLastModifiedBy()
                                                                                        : latestResult.getCreatedBy())
                                                                        : null,
                                                        sample.getLastModifiedAt());
                                })
                                .toList();
        }

        private int flagSeverity(ResultFlag flag) {
                return switch (flag) {
                        case NORMAL -> 0;
                        case LOW, HIGH -> 1;
                        case CRITICAL_LOW, CRITICAL_HIGH -> 2;
                };
        }
}