package com.uom.lims.repository;

import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.entity.TestResultParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestResultParameterRepository extends JpaRepository<TestResultParameterEntity, UUID> {

    List<TestResultParameterEntity> findByTestResultOrderBySortOrderAsc(TestResultEntity testResult);
}