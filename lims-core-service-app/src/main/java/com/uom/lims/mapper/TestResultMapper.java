package com.uom.lims.mapper;

import com.uom.lims.api.verification.dto.response.TestResultDetailResponse;
import com.uom.lims.api.verification.dto.response.TestResultParameterResponse;
import com.uom.lims.api.verification.dto.response.TestResultSummaryResponse;
import com.uom.lims.entity.TestResultEntity;
import com.uom.lims.entity.TestResultParameterEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestResultMapper {

    public TestResultSummaryResponse toSummaryResponse(TestResultEntity entity) {
        return TestResultSummaryResponse.builder()
                .resultId(entity.getId().toString())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .technicianName(entity.getTechnicallyVerifiedBy())
                .pathologistName(entity.getClinicallyAuthorizedBy())
                .build();
    }

    public TestResultDetailResponse toDetailResponse(
            TestResultEntity entity,
            List<TestResultParameterEntity> parameters) {

        return TestResultDetailResponse.builder()
                .resultId(entity.getId().toString())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .technicianName(entity.getTechnicallyVerifiedBy())
                .pathologistName(entity.getClinicallyAuthorizedBy())
                .parameters(toParameterResponses(parameters))
                .clinicalNote(entity.getClinicalNote())
                .mltNotes(entity.getMltNotes())
                .build();
    }

    private List<TestResultParameterResponse> toParameterResponses(
            List<TestResultParameterEntity> parameters) {

        return parameters.stream()
                .map(this::toParameterResponse)
                .toList();
    }

    private TestResultParameterResponse toParameterResponse(TestResultParameterEntity entity) {
        return TestResultParameterResponse.builder()
                .parameterCode(entity.getParameterCode())
                .parameterName(entity.getParameterName())
                .resultValue(entity.getResultValue())
                .resultText(entity.getResultText())
                .unit(entity.getUnit())
                .referenceRangeLow(entity.getReferenceRangeLow())
                .referenceRangeHigh(entity.getReferenceRangeHigh())
                .flag(entity.getFlag() == null ? null : entity.getFlag().name())
                .build();
    }
}