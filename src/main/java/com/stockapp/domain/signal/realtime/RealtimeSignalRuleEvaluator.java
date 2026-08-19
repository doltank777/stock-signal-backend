package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.rule.RuleEvaluationSupport;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricCalculator;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RealtimeSignalRuleEvaluator {

    private final RealtimeSignalMetricCalculator metricCalculator;
    private final RuleEvaluationSupport evaluationSupport;

    public boolean evaluate(
            RealtimeSignalRule rule,
            RealtimeSignalMetricContext context) {
        if (rule == null) throw new IllegalArgumentException("rule is required");
        if (context == null) throw new IllegalArgumentException("context is required");

        Optional<BigDecimal> left = metricCalculator.calculate(
                rule.leftMetric(), rule.leftPeriod(), context);
        if (left.isEmpty()) return false;

        Optional<BigDecimal> right = rule.rightType() == ScreeningRightType.VALUE
                ? Optional.of(rule.rightValue())
                : metricCalculator.calculate(
                        rule.rightMetric(), rule.rightPeriod(), context);
        if (right.isEmpty()) return false;

        return evaluationSupport.compare(
                rule.operator(), left.get().compareTo(right.get()));
    }
}
