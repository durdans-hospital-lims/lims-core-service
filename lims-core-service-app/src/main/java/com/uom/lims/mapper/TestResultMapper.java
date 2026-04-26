package com.uom.lims.mapper;

import com.uom.lims.api.verification.dto.response.TestResultDetailResponse;
import com.uom.lims.api.verification.dto.response.TestResultParameterResponse;
import com.uom.lims.api.verification.dto.response.TestResultSummaryResponse;
import com.uom.lims.entity.TestResultEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class TestResultMapper {

    public TestResultSummaryResponse toSummaryResponse(TestResultEntity entity) {
        return TestResultSummaryResponse.builder()
                .resultId(entity.getId().toString())
                .status(entity.getStatus() == null ? null : entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getLastModifiedAt())
                .technicianName(entity.getTechnicallyVerifiedBy())
                .pathologistName(entity.getClinicallyAuthorizedBy())
                .build();
    }

    public TestResultDetailResponse toDetailResponse(TestResultEntity entity) {
        return TestResultDetailResponse.builder()
                .resultId(entity.getId().toString())
                .status(entity.getStatus() == null ? null : entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getLastModifiedAt())
                .technicianName(entity.getTechnicallyVerifiedBy())
                .pathologistName(entity.getClinicallyAuthorizedBy())
                .parameters(List.of(toParameterResponse(entity)))
                .clinicalNote(entity.getClinicalNote())
                .mltNotes(entity.getMltNotes())
                .build();
    }

    private TestResultParameterResponse toParameterResponse(TestResultEntity entity) {
        return TestResultParameterResponse.builder()
                .parameterCode(entity.getParameter().getId().toString())
                .parameterName(entity.getParameter().getName())
                .resultValue(toBigDecimal(entity.getResultValue()))
                .resultText(entity.getResultValue())
                .unit(entity.getParameter().getUnit())
                .referenceRangeLow(entity.getParameter().getRefLow())
                .referenceRangeHigh(entity.getParameter().getRefHigh())
                .flag(entity.getFlag() == null ? null : entity.getFlag().name())
                .build();
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
