package com.stockapp.domain.screening.rule;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.screening.metric.ScreeningMetricCalculator;
import com.stockapp.domain.screening.metric.StockMetricContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningRuleEvaluatorTest {

    @Mock
    private ScreeningMetricCalculator metricCalculator;

    @Mock
    private StockMetricContext context;

    private ScreeningRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ScreeningRuleEvaluator(metricCalculator);
    }

    @Test
    void rejectsNullRuleAndContext() {
        assertThatThrownBy(() -> evaluator.evaluate(null, context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(valueRule(
                ScreeningOperator.EQUAL, "1", null, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedRuleFields() {
        assertMalformedRule(null, ScreeningOperator.EQUAL,
                ScreeningRightType.VALUE, BigDecimal.ONE, null);
        assertMalformedRule(ScreeningMetric.CURRENT_PRICE, null,
                ScreeningRightType.VALUE, BigDecimal.ONE, null);
        assertMalformedRule(ScreeningMetric.CURRENT_PRICE, ScreeningOperator.EQUAL,
                null, BigDecimal.ONE, null);
        assertMalformedRule(ScreeningMetric.CURRENT_PRICE, ScreeningOperator.EQUAL,
                ScreeningRightType.VALUE, null, null);
        assertMalformedRule(ScreeningMetric.CURRENT_PRICE, ScreeningOperator.EQUAL,
                ScreeningRightType.METRIC, null, null);
    }

    @Test
    void rejectsSignalAndNullStageRules() {
        SearchConditionRule signal = SearchConditionRule.createValueRule(
                ScreeningStage.SIGNAL, ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.EQUAL, BigDecimal.ONE, null, 1);
        SearchConditionRule noStage = mock(SearchConditionRule.class);

        assertThatThrownBy(() -> evaluator.evaluate(signal, context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(noStage, context))
                .isInstanceOf(IllegalArgumentException.class);
        verify(metricCalculator, never()).calculate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void evaluatesValueRuleWithOneCalculatorCall() {
        SearchConditionRule rule = valueRule(
                ScreeningOperator.GREATER_THAN, "100", null, 1);
        when(metricCalculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(new BigDecimal("101")));

        assertThat(evaluator.evaluate(rule, context)).isTrue();
        verify(metricCalculator).calculate(
                ScreeningMetric.CURRENT_PRICE, null, context);
    }

    @Test
    void unavailableLeftValueReturnsFalseWithoutAnotherCalculation() {
        SearchConditionRule rule = valueRule(
                ScreeningOperator.GREATER_THAN, "100", null, 1);
        when(metricCalculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.empty());

        assertThat(evaluator.evaluate(rule, context)).isFalse();
        verify(metricCalculator, times(1)).calculate(
                ScreeningMetric.CURRENT_PRICE, null, context);
    }

    @Test
    void evaluatesMetricRuleUsingExactMetricPeriodAndContext() {
        SearchConditionRule rule = SearchConditionRule.createMetricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN,
                ScreeningMetric.MOVING_AVERAGE, 20,
                null, 1);
        when(metricCalculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(new BigDecimal("101")));
        when(metricCalculator.calculate(
                ScreeningMetric.MOVING_AVERAGE, 20, context))
                .thenReturn(Optional.of(new BigDecimal("100")));

        assertThat(evaluator.evaluate(rule, context)).isTrue();
        verify(metricCalculator).calculate(
                ScreeningMetric.CURRENT_PRICE, null, context);
        verify(metricCalculator).calculate(
                ScreeningMetric.MOVING_AVERAGE, 20, context);
    }

    @Test
    void unavailableMetricValuesReturnFalse() {
        SearchConditionRule rule = SearchConditionRule.createMetricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.VOLUME, null,
                ScreeningOperator.GREATER_THAN,
                ScreeningMetric.AVERAGE_VOLUME, 5,
                null, 1);
        when(metricCalculator.calculate(ScreeningMetric.VOLUME, null, context))
                .thenReturn(Optional.of(BigDecimal.TEN))
                .thenReturn(Optional.empty());
        when(metricCalculator.calculate(ScreeningMetric.AVERAGE_VOLUME, 5, context))
                .thenReturn(Optional.empty());

        assertThat(evaluator.evaluate(rule, context)).isFalse();
        assertThat(evaluator.evaluate(rule, context)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "GREATER_THAN,11,10,true",
            "GREATER_THAN,10,10,false",
            "GREATER_THAN,9,10,false",
            "GREATER_THAN_OR_EQUAL,11,10,true",
            "GREATER_THAN_OR_EQUAL,10,10,true",
            "GREATER_THAN_OR_EQUAL,9,10,false",
            "LESS_THAN,11,10,false",
            "LESS_THAN,10,10,false",
            "LESS_THAN,9,10,true",
            "LESS_THAN_OR_EQUAL,11,10,false",
            "LESS_THAN_OR_EQUAL,10,10,true",
            "LESS_THAN_OR_EQUAL,9,10,true",
            "EQUAL,11,10,false",
            "EQUAL,10,10,true",
            "EQUAL,9,10,false"
    })
    void evaluatesEveryOperatorAndBoundary(
            ScreeningOperator operator,
            String left,
            String right,
            boolean expected
    ) {
        SearchConditionRule rule = valueRule(operator, right, null, 1);
        when(metricCalculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(new BigDecimal(left)));

        assertThat(evaluator.evaluate(rule, context)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1.5,1.500000,true", "1.500001,1.500000,false", "0.3333333333333333333333333333333333,0.3333333333333333333333333333333333,true"})
    void equalUsesNumericComparisonWithoutScaleOrPrecisionChanges(
            String left,
            String right,
            boolean expected
    ) {
        SearchConditionRule rule = valueRule(
                ScreeningOperator.EQUAL, right, null, 1);
        when(metricCalculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(new BigDecimal(left)));

        assertThat(evaluator.evaluate(rule, context)).isEqualTo(expected);
    }

    @Test
    void calculatorPeriodExceptionsPropagateForLeftAndRight() {
        SearchConditionRule leftInvalid = SearchConditionRule.createValueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.MOVING_AVERAGE, null,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ONE, null, 1);
        when(metricCalculator.calculate(
                ScreeningMetric.MOVING_AVERAGE, null, context))
                .thenThrow(new IllegalArgumentException("invalid left period"));

        assertThatThrownBy(() -> evaluator.evaluate(leftInvalid, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid left period");

        reset(metricCalculator);

        SearchConditionRule rightInvalid = SearchConditionRule.createMetricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN,
                ScreeningMetric.MOVING_AVERAGE, null,
                null, 1);
        when(metricCalculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(BigDecimal.TEN));
        when(metricCalculator.calculate(
                ScreeningMetric.MOVING_AVERAGE, null, context))
                .thenThrow(new IllegalArgumentException("invalid right period"));

        assertThatThrownBy(() -> evaluator.evaluate(rightInvalid, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid right period");
    }

    @Test
    void logicalOperatorRuleOrderAndSameMetricDoNotChangeNumericEvaluation() {
        SearchConditionRule first = SearchConditionRule.createMetricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN_OR_EQUAL,
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningLogicalOperator.OR, 99);
        when(metricCalculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, context))
                .thenReturn(Optional.of(new BigDecimal("10")));

        assertThat(evaluator.evaluate(first, context)).isTrue();
        verify(metricCalculator, times(2)).calculate(
                ScreeningMetric.CURRENT_PRICE, null, context);
    }

    private SearchConditionRule valueRule(
            ScreeningOperator operator,
            String right,
            ScreeningLogicalOperator logicalOperator,
            int ruleOrder
    ) {
        return SearchConditionRule.createValueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE,
                null,
                operator,
                new BigDecimal(right),
                logicalOperator,
                ruleOrder);
    }

    private void assertMalformedRule(
            ScreeningMetric leftMetric,
            ScreeningOperator operator,
            ScreeningRightType rightType,
            BigDecimal rightValue,
            ScreeningMetric rightMetric
    ) {
        SearchConditionRule rule = mock(SearchConditionRule.class);
        lenient().when(rule.getStage()).thenReturn(ScreeningStage.SCREENING);
        lenient().when(rule.getLeftMetric()).thenReturn(leftMetric);
        lenient().when(rule.getOperator()).thenReturn(operator);
        lenient().when(rule.getRightType()).thenReturn(rightType);
        lenient().when(rule.getRightValue()).thenReturn(rightValue);
        lenient().when(rule.getRightMetric()).thenReturn(rightMetric);

        assertThatThrownBy(() -> evaluator.evaluate(rule, context))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
