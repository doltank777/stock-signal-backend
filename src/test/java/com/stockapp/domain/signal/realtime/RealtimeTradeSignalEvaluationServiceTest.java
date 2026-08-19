package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.realtime.RealtimeWatchTarget;
import com.stockapp.domain.screening.realtime.RealtimeWatchTargetRegistry;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeTradeSignalEvaluationServiceTest {

    @Test
    void returnsEmptyWithoutTargetAndDoesNotCallEvaluator() {
        RealtimeWatchTargetRegistry registry = mock(RealtimeWatchTargetRegistry.class);
        RealtimeSignalEvaluator evaluator = mock(RealtimeSignalEvaluator.class);
        KisRealtimeTradePrice trade = trade();
        when(registry.findByStockCode("005930")).thenReturn(Optional.empty());
        RealtimeTradeSignalEvaluationService service =
                new RealtimeTradeSignalEvaluationService(registry, evaluator);

        assertThat(service.evaluate(trade)).isEmpty();
        verify(evaluator, never()).evaluate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void evaluatesTargetOnceAndReturnsAllConditionResultsInOriginalOrder() {
        RealtimeWatchTargetRegistry registry = mock(RealtimeWatchTargetRegistry.class);
        RealtimeSignalEvaluator evaluator = mock(RealtimeSignalEvaluator.class);
        RealtimeWatchTarget target = new RealtimeWatchTarget(
                10L, "005930", List.of(3L, 1L, 2L));
        KisRealtimeTradePrice trade = trade();
        RealtimeSignalEvaluationResult result = result();
        when(registry.findByStockCode("005930")).thenReturn(Optional.of(target));
        when(evaluator.evaluate(target, trade)).thenReturn(result);
        RealtimeTradeSignalEvaluationService service =
                new RealtimeTradeSignalEvaluationService(registry, evaluator);

        Optional<RealtimeSignalEvaluationResult> evaluated = service.evaluate(trade);

        assertThat(evaluated).containsSame(result);
        assertThat(evaluated.orElseThrow().conditionResults())
                .extracting(RealtimeSignalConditionResult::conditionId)
                .containsExactly(3L, 1L, 2L);
        assertThat(evaluated.orElseThrow().conditionResults().stream()
                .filter(RealtimeSignalConditionResult::matched)
                .map(RealtimeSignalConditionResult::conditionId))
                .containsExactly(1L, 2L);
        verify(evaluator).evaluate(target, trade);
    }

    @Test
    void propagatesEvaluationFailureForHandlerLevelIsolation() {
        RealtimeWatchTargetRegistry registry = mock(RealtimeWatchTargetRegistry.class);
        RealtimeSignalEvaluator evaluator = mock(RealtimeSignalEvaluator.class);
        RealtimeWatchTarget target = new RealtimeWatchTarget(
                10L, "005930", List.of(1L));
        KisRealtimeTradePrice trade = trade();
        RuntimeException failure = new IllegalStateException("definition mismatch");
        when(registry.findByStockCode("005930")).thenReturn(Optional.of(target));
        when(evaluator.evaluate(target, trade)).thenThrow(failure);
        RealtimeTradeSignalEvaluationService service =
                new RealtimeTradeSignalEvaluationService(registry, evaluator);

        assertThatThrownBy(() -> service.evaluate(trade)).isSameAs(failure);
    }

    private KisRealtimeTradePrice trade() {
        return KisRealtimeTradePrice.builder()
                .stockCode("005930")
                .currentPrice(71_000L)
                .accumulatedVolume(2_500_000L)
                .tradeDateTime(LocalDateTime.of(2026, 8, 19, 10, 0))
                .build();
    }

    private RealtimeSignalEvaluationResult result() {
        return new RealtimeSignalEvaluationResult(
                10L, "005930", LocalDateTime.of(2026, 8, 19, 10, 0),
                List.of(
                        new RealtimeSignalConditionResult(3L, false),
                        new RealtimeSignalConditionResult(1L, true),
                        new RealtimeSignalConditionResult(2L, true)));
    }
}
