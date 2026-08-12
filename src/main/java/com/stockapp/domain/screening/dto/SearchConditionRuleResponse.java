package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SearchConditionRuleResponse {

    private Long id;

    private ScreeningStage stage;

    private ScreeningMetric leftMetric;

    private Integer leftPeriod;

    private ScreeningOperator operator;

    private ScreeningRightType rightType;

    private BigDecimal rightValue;

    private ScreeningMetric rightMetric;

    private Integer rightPeriod;

    private ScreeningLogicalOperator logicalOperator;

    private int ruleOrder;

    public static SearchConditionRuleResponse from(
            SearchConditionRule rule
    ) {
        return SearchConditionRuleResponse.builder()
                .id(rule.getId())
                .stage(rule.getStage())
                .leftMetric(rule.getLeftMetric())
                .leftPeriod(rule.getLeftPeriod())
                .operator(rule.getOperator())
                .rightType(rule.getRightType())
                .rightValue(rule.getRightValue())
                .rightMetric(rule.getRightMetric())
                .rightPeriod(rule.getRightPeriod())
                .logicalOperator(rule.getLogicalOperator())
                .ruleOrder(rule.getRuleOrder())
                .build();
    }
}