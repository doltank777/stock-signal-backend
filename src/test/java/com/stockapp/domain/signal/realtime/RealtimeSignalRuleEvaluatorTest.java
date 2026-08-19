package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.rule.RuleEvaluationSupport;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricCalculator;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeSignalRuleEvaluatorTest {

    private RealtimeSignalMetricCalculator calculator;
    private RealtimeSignalMetricContext context;
    private RealtimeSignalRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        calculator = mock(RealtimeSignalMetricCalculator.class);
        context = mock(RealtimeSignalMetricContext.class);
        evaluator = new RealtimeSignalRuleEvaluator(
                calculator, new RuleEvaluationSupport());
    }

    @ParameterizedTest
    @CsvSource({
            "GREATER_THAN,11,10,true", "GREATER_THAN,10,10,false",
            "GREATER_THAN_OR_EQUAL,10,10,true",
            "LESS_THAN,9,10,true", "LESS_THAN,10,10,false",
            "LESS_THAN_OR_EQUAL,10,10,true",
            "EQUAL,10.0,10.00,true", "EQUAL,11,10,false"
    })
    void evaluatesEverySupportedComparisonOperator(
            ScreeningOperator operator, String left, String right,
            boolean expected) {
        RealtimeSignalRule rule = valueRule(
                ScreeningMetric.CURRENT_PRICE, null, operator,
                new BigDecimal(right), null, 1);
        when(calculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(new BigDecimal(left)));

        assertThat(evaluator.evaluate(rule, context)).isEqualTo(expected);
    }

    @Test
    void evaluatesValueAndMetricOperandsAndUnavailableAsFalse() {
        RealtimeSignalRule valueRule = valueRule(
                ScreeningMetric.VOLUME_RATIO, 20,
                ScreeningOperator.GREATER_THAN_OR_EQUAL,
                new BigDecimal("2.5"), null, 1);
        when(calculator.calculate(
                ScreeningMetric.VOLUME_RATIO, 20, context))
                .thenReturn(Optional.of(new BigDecimal("2.6")));
        assertThat(evaluator.evaluate(valueRule, context)).isTrue();

        RealtimeSignalRule metricRule = metricRule(
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN,
                ScreeningMetric.MOVING_AVERAGE, 10, null, 1);
        when(calculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(new BigDecimal("101")));
        when(calculator.calculate(
                ScreeningMetric.MOVING_AVERAGE, 10, context))
                .thenReturn(Optional.of(new BigDecimal("100")));
        assertThat(evaluator.evaluate(metricRule, context)).isTrue();

        when(calculator.calculate(
                ScreeningMetric.MOVING_AVERAGE, 10, context))
                .thenReturn(Optional.empty());
        assertThat(evaluator.evaluate(metricRule, context)).isFalse();
    }

    static RealtimeSignalRule valueRule(
            ScreeningMetric metric, Integer period, ScreeningOperator operator,
            BigDecimal value, ScreeningLogicalOperator logical, int order) {
        return new RealtimeSignalRule(
                order, logical, metric, period, operator,
                ScreeningRightType.VALUE, value, null, null);
    }

    static RealtimeSignalRule metricRule(
            ScreeningMetric left, Integer leftPeriod, ScreeningOperator operator,
            ScreeningMetric right, Integer rightPeriod,
            ScreeningLogicalOperator logical, int order) {
        return new RealtimeSignalRule(
                order, logical, left, leftPeriod, operator,
                ScreeningRightType.METRIC, null, right, rightPeriod);
    }
}
