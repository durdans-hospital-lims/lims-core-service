package com.uom.lims.repository;

import com.uom.lims.api.verification.enums.ResultStatus;
import com.uom.lims.entity.TestResultEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResultEntity, UUID> {

    List<TestResultEntity> findBySampleId(UUID sampleId);

    List<TestResultEntity> findBySampleIdIn(List<UUID> sampleIds);

    Optional<TestResultEntity> findBySampleIdAndParameterId(UUID sampleId, UUID parameterId);

    Page<TestResultEntity> findByStatusAndDraftFalse(ResultStatus status, Pageable pageable);

    Page<TestResultEntity> findByStatusInAndDraftFalse(List<ResultStatus> statuses, Pageable pageable);

    List<TestResultEntity> findByStatusInAndDraftFalse(List<ResultStatus> statuses);

    @Query("""
            select tr
            from TestResultEntity tr
            join tr.sample s
            join s.orderItem oi
            join oi.order o
            where o.patientId = :patientId
              and oi.testId = :testId
              and s.id <> :sampleId
              and coalesce(s.collectedAt, s.createdAt) < :currentVisitAt
              and tr.deleted = false
              and s.deleted = false
              and oi.deleted = false
              and o.deleted = false
            order by coalesce(s.collectedAt, s.createdAt) desc, s.id desc, tr.createdAt desc
            """)
    List<TestResultEntity> findPreviousResultsForPatientAndTest(
            @Param("patientId") String patientId,
            @Param("testId") UUID testId,
            @Param("sampleId") UUID sampleId,
            @Param("currentVisitAt") Instant currentVisitAt
    );
}
