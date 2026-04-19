package com.uom.lims.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ReportDispatchItemRepository
        extends JpaRepository<ReportDispatchItemEntity, UUID>, JpaSpecificationExecutor<ReportDispatchItemEntity> {

    Optional<ReportDispatchItemEntity> findByReportReferenceAndBranchCode(String reportReference, String branchCode);
}
