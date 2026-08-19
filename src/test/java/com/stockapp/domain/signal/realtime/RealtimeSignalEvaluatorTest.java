package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.realtime.RealtimeWatchTarget;
import com.stockapp.domain.screening.rule.RuleEvaluationSupport;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricContext;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricContextFactory;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSignalEvaluatorTest {

    private static final LocalDateTime TRADE_TIME =
            LocalDateTime.of(2026, 8, 19, 10, 0);

    @Test
    void evaluatesAllConditionsInTargetOrderWithOneSharedContext() {
        RealtimeSignalConditionDefinitionCache cache =
                mock(RealtimeSignalConditionDefinitionCache.class);
        RealtimeSignalRequirementAnalyzer analyzer =
                new RealtimeSignalRequirementAnalyzer();
        RealtimeSignalMetricContextFactory factory =
                mock(RealtimeSignalMetricContextFactory.class);
        RealtimeSignalRuleEvaluator ruleEvaluator =
                mock(RealtimeSignalRuleEvaluator.class);
        RealtimeSignalMetricContext context = mock(RealtimeSignalMetricContext.class);
        RealtimeWatchTarget target = target("005930");
        KisRealtimeTradePrice trade = trade("005930");

        RealtimeSignalRule condition3First = metricRule(10, null, 1);
        RealtimeSignalRule condition3Second = volumeRule(
                20, "2.5", ScreeningLogicalOperator.AND, 2);
        RealtimeSignalRule condition1 = metricRule(5, null, 1);
        RealtimeSignalRule condition2First = metricRule(5, null, 1);
        RealtimeSignalRule condition2Second = volumeRule(
                20, "2.0", ScreeningLogicalOperator.AND, 2);
        Map<Long, RealtimeSignalConditionDefinition> definitions =
                new LinkedHashMap<>();
        definitions.put(1L, definition(1L, condition1));
        definitions.put(2L, definition(2L, condition2First, condition2Second));
        definitions.put(3L, definition(3L, condition3First, condition3Second));
        when(cache.getAll(List.of(3L, 1L, 2L))).thenReturn(definitions);
        when(factory.create(target, trade, 20)).thenReturn(context);
        when(ruleEvaluator.evaluate(condition3First, context)).thenReturn(true);
        when(ruleEvaluator.evaluate(condition3Second, context)).thenReturn(false);
        when(ruleEvaluator.evaluate(condition1, context)).thenReturn(true);
        when(ruleEvaluator.evaluate(condition2First, context)).thenReturn(true);
        when(ruleEvaluator.evaluate(condition2Second, context)).thenReturn(true);
        RealtimeSignalEvaluator evaluator = new RealtimeSignalEvaluator(
                cache, analyzer, factory, ruleEvaluator,
                new RuleEvaluationSupport());

        RealtimeSignalEvaluationResult result = evaluator.evaluate(target, trade);

        assertThat(result.conditionResults())
                .extracting(RealtimeSignalConditionResult::conditionId)
                .containsExactly(3L, 1L, 2L);
        assertThat(result.conditionResults())
                .extracting(RealtimeSignalConditionResult::matched)
                .containsExactly(false, true, true);
        verify(factory).create(target, trade, 20);
    }

    @Test
    void rejectsStockCodeMismatchBeforeConditionLookup() {
        RealtimeSignalConditionDefinitionCache cache =
                mock(RealtimeSignalConditionDefinitionCache.class);
        RealtimeSignalEvaluator evaluator = new RealtimeSignalEvaluator(
                cache, mock(RealtimeSignalRequirementAnalyzer.class),
                mock(RealtimeSignalMetricContextFactory.class),
                mock(RealtimeSignalRuleEvaluator.class),
                new RuleEvaluationSupport());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> evaluator.evaluate(
                        target("005930"), trade("000660")))
                .withMessage("target and trade stockCode must match");
        verify(cache, never()).getAll(org.mockito.ArgumentMatchers.anyList());
    }

    private RealtimeWatchTarget target(String stockCode) {
        return new RealtimeWatchTarget(10L, stockCode, List.of(3L, 1L, 2L));
    }

    private KisRealtimeTradePrice trade(String stockCode) {
        return KisRealtimeTradePrice.builder()
                .stockCode(stockCode).currentPrice(71_000L)
                .accumulatedVolume(2_500_000L).tradeDateTime(TRADE_TIME)
                .build();
    }

    private RealtimeSignalConditionDefinition definition(
            Long id, RealtimeSignalRule... rules) {
        return new RealtimeSignalConditionDefinition(id, List.of(rules));
    }

    private RealtimeSignalRule metricRule(
            int period, ScreeningLogicalOperator logical, int order) {
        return RealtimeSignalRuleEvaluatorTest.metricRule(
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN,
                ScreeningMetric.MOVING_AVERAGE, period, logical, order);
    }

    private RealtimeSignalRule volumeRule(
            int period, String threshold,
            ScreeningLogicalOperator logical, int order) {
        return RealtimeSignalRuleEvaluatorTest.valueRule(
                ScreeningMetric.VOLUME_RATIO, period,
                ScreeningOperator.GREATER_THAN_OR_EQUAL,
                new BigDecimal(threshold), logical, order);
    }
}
