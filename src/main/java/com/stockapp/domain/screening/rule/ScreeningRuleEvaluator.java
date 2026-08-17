package com.stockapp.domain.screening.rule;

import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.screening.metric.ScreeningMetricCalculator;
import com.stockapp.domain.screening.metric.StockMetricContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScreeningRuleEvaluator {

    private final ScreeningMetricCalculator metricCalculator;

    public boolean evaluate(
            SearchConditionRule rule,
            StockMetricContext context
    ) {
        validate(rule, context);

        Optional<BigDecimal> left = metricCalculator.calculate(
                rule.getLeftMetric(), rule.getLeftPeriod(), context);
        if (left.isEmpty()) {
            return false;
        }

        Optional<BigDecimal> right = resolveRight(rule, context);
        if (right.isEmpty()) {
            return false;
        }

        int comparison = left.get().compareTo(right.get());
        return compare(rule.getOperator(), comparison);
    }

    private Optional<BigDecimal> resolveRight(
            SearchConditionRule rule,
            StockMetricContext context
    ) {
        if (rule.getRightType() == ScreeningRightType.VALUE) {
            return Optional.of(rule.getRightValue());
        }
        return metricCalculator.calculate(
                rule.getRightMetric(), rule.getRightPeriod(), context);
    }

    private boolean compare(
            ScreeningOperator operator,
            int comparison
    ) {
        return switch (operator) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
        };
    }

    private void validate(
            SearchConditionRule rule,
            StockMetricContext context
    ) {
        if (rule == null) {
            throw new IllegalArgumentException("rule is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        if (rule.getStage() != ScreeningStage.SCREENING) {
            throw new IllegalArgumentException("only SCREENING rules can be evaluated");
        }
        if (rule.getLeftMetric() == null) {
            throw new IllegalArgumentException("leftMetric is required");
        }
        if (rule.getOperator() == null) {
            throw new IllegalArgumentException("operator is required");
        }
        if (rule.getRightType() == null) {
            throw new IllegalArgumentException("rightType is required");
        }
        if (rule.getRightType() == ScreeningRightType.VALUE
                && rule.getRightValue() == null) {
            throw new IllegalArgumentException("rightValue is required for VALUE rules");
        }
        if (rule.getRightType() == ScreeningRightType.METRIC
                && rule.getRightMetric() == null) {
            throw new IllegalArgumentException("rightMetric is required for METRIC rules");
        }
    }
}
