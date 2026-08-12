package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class SearchConditionRuleRequest {

    @NotNull(message = "조건 단계는 필수입니다.")
    private ScreeningStage stage;

    @NotNull(message = "왼쪽 지표는 필수입니다.")
    private ScreeningMetric leftMetric;

    @Min(value = 1, message = "왼쪽 기간은 1 이상이어야 합니다.")
    private Integer leftPeriod;

    @NotNull(message = "비교 연산자는 필수입니다.")
    private ScreeningOperator operator;

    @NotNull(message = "비교 대상 타입은 필수입니다.")
    private ScreeningRightType rightType;

    private BigDecimal rightValue;

    private ScreeningMetric rightMetric;

    @Min(value = 1, message = "오른쪽 기간은 1 이상이어야 합니다.")
    private Integer rightPeriod;

    private ScreeningLogicalOperator logicalOperator;

    @Min(value = 1, message = "조건 순서는 1 이상이어야 합니다.")
    private int ruleOrder;
}