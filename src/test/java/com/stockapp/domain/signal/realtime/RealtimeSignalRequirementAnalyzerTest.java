package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeSignalRequirementAnalyzerTest {

    private final RealtimeSignalRequirementAnalyzer analyzer =
            new RealtimeSignalRequirementAnalyzer();

    @Test
    void findsMaximumPeriodAcrossLeftAndRightMetrics() {
        RealtimeSignalConditionDefinition first = definition(1L,
                RealtimeSignalRuleEvaluatorTest.metricRule(
                        ScreeningMetric.CURRENT_PRICE, null,
                        ScreeningOperator.GREATER_THAN,
                        ScreeningMetric.MOVING_AVERAGE, 5, null, 1),
                RealtimeSignalRuleEvaluatorTest.metricRule(
                        ScreeningMetric.CURRENT_PRICE, null,
                        ScreeningOperator.GREATER_THAN,
                        ScreeningMetric.MOVING_AVERAGE, 10,
                        com.stockapp.domain.screening.ScreeningLogicalOperator.AND, 2),
                RealtimeSignalRuleEvaluatorTest.valueRule(
                        ScreeningMetric.VOLUME_RATIO, 20,
                        ScreeningOperator.GREATER_THAN_OR_EQUAL,
                        BigDecimal.valueOf(2),
                        com.stockapp.domain.screening.ScreeningLogicalOperator.AND, 3));
        assertThat(analyzer.requiredDailyPeriod(List.of(first))).isEqualTo(20);

        RealtimeSignalConditionDefinition noHistory = definition(2L,
                RealtimeSignalRuleEvaluatorTest.valueRule(
                        ScreeningMetric.CURRENT_PRICE, null,
                        ScreeningOperator.GREATER_THAN,
                        BigDecimal.valueOf(10_000), null, 1));
        assertThat(analyzer.requiredDailyPeriod(List.of(noHistory))).isZero();

        RealtimeSignalConditionDefinition bothSides = definition(3L,
                RealtimeSignalRuleEvaluatorTest.metricRule(
                        ScreeningMetric.MOVING_AVERAGE, 60,
                        ScreeningOperator.GREATER_THAN,
                        ScreeningMetric.MOVING_AVERAGE, 120, null, 1));
        assertThat(analyzer.requiredDailyPeriod(List.of(bothSides)))
                .isEqualTo(120);
    }

    private RealtimeSignalConditionDefinition definition(
            Long id, RealtimeSignalRule... rules) {
        return new RealtimeSignalConditionDefinition(id, List.of(rules));
    }
}
