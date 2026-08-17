package com.stockapp.domain.screening.rule;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.screening.metric.StockMetricContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningConditionEvaluatorTest {

    @Mock
    private ScreeningRuleEvaluator ruleEvaluator;

    @Mock
    private StockMetricContext context;

    private ScreeningConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ScreeningConditionEvaluator(ruleEvaluator);
    }

    @Test
    void rejectsNullConditionAndContext() {
        assertThatThrownBy(() -> evaluator.evaluate(null, context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(condition(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evaluatesOnlyScreeningRulesInRuleOrderWithoutMutatingOriginalList() {
        SearchConditionRule screening2 = rule(
                ScreeningStage.SCREENING, 2, ScreeningLogicalOperator.AND);
        SearchConditionRule signal1 = rule(
                ScreeningStage.SIGNAL, 1, null);
        SearchConditionRule screening1 = rule(
                ScreeningStage.SCREENING, 1, null);
        SearchConditionRule signal2 = rule(
                ScreeningStage.SIGNAL, 2, ScreeningLogicalOperator.OR);
        SearchCondition condition = condition(
                screening2, signal1, screening1, signal2);
        List<SearchConditionRule> originalOrder = List.copyOf(condition.getRules());
        when(ruleEvaluator.evaluate(screening1, context)).thenReturn(true);
        when(ruleEvaluator.evaluate(screening2, context)).thenReturn(true);

        assertThat(evaluator.evaluate(condition, context)).isTrue();

        InOrder inOrder = inOrder(ruleEvaluator);
        inOrder.verify(ruleEvaluator).evaluate(screening1, context);
        inOrder.verify(ruleEvaluator).evaluate(screening2, context);
        verify(ruleEvaluator, never()).evaluate(signal1, context);
        verify(ruleEvaluator, never()).evaluate(signal2, context);
        assertThat(condition.getRules()).containsExactlyElementsOf(originalOrder);
    }

    @Test
    void rejectsConditionWithoutScreeningRules() {
        SearchCondition condition = condition(rule(
                ScreeningStage.SIGNAL, 1, null));

        assertThatThrownBy(() -> evaluator.evaluate(condition, context))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ruleEvaluator);
    }

    @Test
    void rejectsInvalidRuleOrders() {
        assertInvalid(condition(rule(ScreeningStage.SCREENING, 0, null)));
        assertInvalid(condition(rule(ScreeningStage.SCREENING, -1, null)));
        assertInvalid(condition(rule(ScreeningStage.SCREENING, 2, null)));
        assertInvalid(condition(
                rule(ScreeningStage.SCREENING, 1, null),
                rule(ScreeningStage.SCREENING, 1, ScreeningLogicalOperator.AND)));
        assertInvalid(condition(
                rule(ScreeningStage.SCREENING, 1, null),
                rule(ScreeningStage.SCREENING, 3, ScreeningLogicalOperator.AND)));
    }

    @Test
    void rejectsInvalidLogicalOperators() {
        assertInvalid(condition(rule(
                ScreeningStage.SCREENING, 1, ScreeningLogicalOperator.AND)));
        assertInvalid(condition(
                rule(ScreeningStage.SCREENING, 1, null),
                rule(ScreeningStage.SCREENING, 2, null)));
    }

    @Test
    void combinesAndAndOrRules() {
        assertCombination(ScreeningLogicalOperator.AND, true, true, true);
        assertCombination(ScreeningLogicalOperator.AND, true, false, false);
        assertCombination(ScreeningLogicalOperator.OR, false, true, true);
        assertCombination(ScreeningLogicalOperator.OR, false, false, false);
    }

    @Test
    void accumulatesLeftToRightWithoutAndPrecedence() {
        SearchConditionRule first = rule(ScreeningStage.SCREENING, 1, null);
        SearchConditionRule second = rule(
                ScreeningStage.SCREENING, 2, ScreeningLogicalOperator.OR);
        SearchConditionRule third = rule(
                ScreeningStage.SCREENING, 3, ScreeningLogicalOperator.AND);
        when(ruleEvaluator.evaluate(first, context)).thenReturn(false);
        when(ruleEvaluator.evaluate(second, context)).thenReturn(true);
        when(ruleEvaluator.evaluate(third, context)).thenReturn(false);

        assertThat(evaluator.evaluate(
                condition(first, second, third), context)).isFalse();
    }

    @Test
    void accumulatesAndThenOrLeftToRight() {
        SearchConditionRule first = rule(ScreeningStage.SCREENING, 1, null);
        SearchConditionRule second = rule(
                ScreeningStage.SCREENING, 2, ScreeningLogicalOperator.AND);
        SearchConditionRule third = rule(
                ScreeningStage.SCREENING, 3, ScreeningLogicalOperator.OR);
        when(ruleEvaluator.evaluate(first, context)).thenReturn(true);
        when(ruleEvaluator.evaluate(second, context)).thenReturn(false);
        when(ruleEvaluator.evaluate(third, context)).thenReturn(true);

        assertThat(evaluator.evaluate(
                condition(first, second, third), context)).isTrue();
    }

    @Test
    void shortCircuitsCurrentAndRuleButContinuesToLaterOrRule() {
        SearchConditionRule first = rule(ScreeningStage.SCREENING, 1, null);
        SearchConditionRule skipped = rule(
                ScreeningStage.SCREENING, 2, ScreeningLogicalOperator.AND);
        SearchConditionRule later = rule(
                ScreeningStage.SCREENING, 3, ScreeningLogicalOperator.OR);
        when(ruleEvaluator.evaluate(first, context)).thenReturn(false);
        when(ruleEvaluator.evaluate(later, context)).thenReturn(true);

        assertThat(evaluator.evaluate(
                condition(first, skipped, later), context)).isTrue();
        verify(ruleEvaluator, never()).evaluate(skipped, context);
        verify(ruleEvaluator).evaluate(later, context);
    }

    @Test
    void shortCircuitsCurrentOrRuleButContinuesToLaterAndRule() {
        SearchConditionRule first = rule(ScreeningStage.SCREENING, 1, null);
        SearchConditionRule skipped = rule(
                ScreeningStage.SCREENING, 2, ScreeningLogicalOperator.OR);
        SearchConditionRule later = rule(
                ScreeningStage.SCREENING, 3, ScreeningLogicalOperator.AND);
        when(ruleEvaluator.evaluate(first, context)).thenReturn(true);
        when(ruleEvaluator.evaluate(later, context)).thenReturn(false);

        assertThat(evaluator.evaluate(
                condition(first, skipped, later), context)).isFalse();
        verify(ruleEvaluator, never()).evaluate(skipped, context);
        verify(ruleEvaluator).evaluate(later, context);
    }

    @Test
    void auxiliaryConditionFieldsDoNotAffectEvaluation() {
        SearchConditionRule first = rule(ScreeningStage.SCREENING, 1, null);
        SearchCondition low = condition(false, 0, 0, false, first);
        SearchCondition high = condition(true, 1000, 100, true, first);
        when(ruleEvaluator.evaluate(first, context)).thenReturn(true);

        assertThat(evaluator.evaluate(low, context)).isTrue();
        assertThat(evaluator.evaluate(high, context)).isTrue();
    }

    @Test
    void propagatesRuleEvaluatorException() {
        SearchConditionRule first = rule(ScreeningStage.SCREENING, 1, null);
        when(ruleEvaluator.evaluate(first, context))
                .thenThrow(new IllegalArgumentException("malformed rule"));

        assertThatThrownBy(() -> evaluator.evaluate(condition(first), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("malformed rule");
    }

    private void assertCombination(
            ScreeningLogicalOperator logicalOperator,
            boolean firstResult,
            boolean secondResult,
            boolean expected
    ) {
        SearchConditionRule first = rule(ScreeningStage.SCREENING, 1, null);
        SearchConditionRule second = rule(
                ScreeningStage.SCREENING, 2, logicalOperator);
        when(ruleEvaluator.evaluate(first, context)).thenReturn(firstResult);
        when(ruleEvaluator.evaluate(second, context)).thenReturn(secondResult);

        assertThat(evaluator.evaluate(condition(first, second), context))
                .isEqualTo(expected);
    }

    private void assertInvalid(SearchCondition condition) {
        assertThatThrownBy(() -> evaluator.evaluate(condition, context))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ruleEvaluator);
    }

    private SearchCondition condition(SearchConditionRule... rules) {
        return condition(true, 100, 80, false, rules);
    }

    private SearchCondition condition(
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled,
            SearchConditionRule... rules
    ) {
        SearchCondition condition = SearchCondition.create(
                "condition", null, enabled, priority,
                screeningScore, realtimeEnabled, null);
        for (SearchConditionRule rule : rules) {
            condition.addRule(rule);
        }
        return condition;
    }

    private SearchConditionRule rule(
            ScreeningStage stage,
            int ruleOrder,
            ScreeningLogicalOperator logicalOperator
    ) {
        return SearchConditionRule.createValueRule(
                stage,
                ScreeningMetric.CURRENT_PRICE,
                null,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ZERO,
                logicalOperator,
                ruleOrder);
    }
}
