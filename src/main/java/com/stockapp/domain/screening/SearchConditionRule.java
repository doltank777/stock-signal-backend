package com.stockapp.domain.screening;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "search_condition_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchConditionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "search_condition_id",
            nullable = false
    )
    private SearchCondition searchCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScreeningStage stage;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "left_metric",
            nullable = false,
            length = 50
    )
    private ScreeningMetric leftMetric;

    @Column(name = "left_period")
    private Integer leftPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ScreeningOperator operator;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "right_type",
            nullable = false,
            length = 20
    )
    private ScreeningRightType rightType;

    @Column(
            name = "right_value",
            precision = 19,
            scale = 6
    )
    private BigDecimal rightValue;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "right_metric",
            length = 50
    )
    private ScreeningMetric rightMetric;

    @Column(name = "right_period")
    private Integer rightPeriod;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "logical_operator",
            length = 10
    )
    private ScreeningLogicalOperator logicalOperator;

    @Column(
            name = "rule_order",
            nullable = false
    )
    private int ruleOrder;

    private SearchConditionRule(
            ScreeningStage stage,
            ScreeningMetric leftMetric,
            Integer leftPeriod,
            ScreeningOperator operator,
            ScreeningRightType rightType,
            BigDecimal rightValue,
            ScreeningMetric rightMetric,
            Integer rightPeriod,
            ScreeningLogicalOperator logicalOperator,
            int ruleOrder
    ) {
        this.stage = stage;
        this.leftMetric = leftMetric;
        this.leftPeriod = leftPeriod;
        this.operator = operator;
        this.rightType = rightType;
        this.rightValue = rightValue;
        this.rightMetric = rightMetric;
        this.rightPeriod = rightPeriod;
        this.logicalOperator = logicalOperator;
        this.ruleOrder = ruleOrder;
    }

    public static SearchConditionRule createValueRule(
            ScreeningStage stage,
            ScreeningMetric leftMetric,
            Integer leftPeriod,
            ScreeningOperator operator,
            BigDecimal rightValue,
            ScreeningLogicalOperator logicalOperator,
            int ruleOrder
    ) {
        return new SearchConditionRule(
                stage,
                leftMetric,
                leftPeriod,
                operator,
                ScreeningRightType.VALUE,
                rightValue,
                null,
                null,
                logicalOperator,
                ruleOrder
        );
    }

    public static SearchConditionRule createMetricRule(
            ScreeningStage stage,
            ScreeningMetric leftMetric,
            Integer leftPeriod,
            ScreeningOperator operator,
            ScreeningMetric rightMetric,
            Integer rightPeriod,
            ScreeningLogicalOperator logicalOperator,
            int ruleOrder
    ) {
        return new SearchConditionRule(
                stage,
                leftMetric,
                leftPeriod,
                operator,
                ScreeningRightType.METRIC,
                null,
                rightMetric,
                rightPeriod,
                logicalOperator,
                ruleOrder
        );
    }

    public void updateValueRule(
            ScreeningStage stage,
            ScreeningMetric leftMetric,
            Integer leftPeriod,
            ScreeningOperator operator,
            BigDecimal rightValue,
            ScreeningLogicalOperator logicalOperator,
            int ruleOrder
    ) {
        this.stage = stage;
        this.leftMetric = leftMetric;
        this.leftPeriod = leftPeriod;
        this.operator = operator;

        this.rightType = ScreeningRightType.VALUE;
        this.rightValue = rightValue;

        this.rightMetric = null;
        this.rightPeriod = null;

        this.logicalOperator = logicalOperator;
        this.ruleOrder = ruleOrder;
    }

    public void updateMetricRule(
            ScreeningStage stage,
            ScreeningMetric leftMetric,
            Integer leftPeriod,
            ScreeningOperator operator,
            ScreeningMetric rightMetric,
            Integer rightPeriod,
            ScreeningLogicalOperator logicalOperator,
            int ruleOrder
    ) {
        this.stage = stage;
        this.leftMetric = leftMetric;
        this.leftPeriod = leftPeriod;
        this.operator = operator;

        this.rightType = ScreeningRightType.METRIC;
        this.rightValue = null;

        this.rightMetric = rightMetric;
        this.rightPeriod = rightPeriod;

        this.logicalOperator = logicalOperator;
        this.ruleOrder = ruleOrder;
    }

    void assignSearchCondition(
            SearchCondition searchCondition
    ) {
        this.searchCondition = searchCondition;
    }
}