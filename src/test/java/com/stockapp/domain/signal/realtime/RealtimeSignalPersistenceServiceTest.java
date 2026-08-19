package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.signal.Signal;
import com.stockapp.domain.signal.SignalRepository;
import com.stockapp.domain.signal.SignalType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RealtimeSignalPersistenceServiceTest {

    private static final LocalDateTime TRADE_TIME =
            LocalDateTime.of(2026, 8, 19, 10, 0);

    private StockRepository stockRepository;
    private SearchConditionRepository conditionRepository;
    private SignalRepository signalRepository;
    private RealtimeSignalPersistenceService service;
    private Stock stock;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        conditionRepository = mock(SearchConditionRepository.class);
        signalRepository = mock(SignalRepository.class);
        service = new RealtimeSignalPersistenceService(
                stockRepository, conditionRepository, signalRepository);
        stock = mock(Stock.class);
    }

    @Test
    void matchedZeroDoesNotAccessPersistenceRepositories() {
        service.persistMatchedSignals(result(
                condition(3L, false), condition(1L, false)));

        verifyNoInteractions(stockRepository, conditionRepository, signalRepository);
    }

    @Test
    void savesOneConditionAwareSignalWithTradeTimeAndNoLegacyMetricFallbacks() {
        SearchCondition condition = conditionEntity(1L);
        prepareStockAndConditions(List.of(1L), List.of(condition));
        when(signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, condition, TRADE_TIME.minusMinutes(30)))
                .thenReturn(false);

        service.persistMatchedSignals(result(condition(1L, true)));

        ArgumentCaptor<Signal> captor = ArgumentCaptor.forClass(Signal.class);
        verify(signalRepository).save(captor.capture());
        Signal saved = captor.getValue();
        assertThat(saved.getStock()).isSameAs(stock);
        assertThat(saved.getSearchCondition()).isSameAs(condition);
        assertThat(saved.getSignalType()).isEqualTo(
                SignalType.SEARCH_CONDITION_MATCH);
        assertThat(saved.getDetectedAt()).isEqualTo(TRADE_TIME);
        assertThat(saved.getBaseValue()).isNull();
        assertThat(saved.getCurrentValue()).isNull();
        assertThat(saved.getChangeRate()).isNull();
    }

    @Test
    void savesEveryMatchedConditionInEvaluationOrder() {
        SearchCondition one = conditionEntity(1L);
        SearchCondition two = conditionEntity(2L);
        prepareStockAndConditions(List.of(1L, 2L), List.of(two, one));
        when(signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, one, TRADE_TIME.minusMinutes(30))).thenReturn(false);
        when(signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, two, TRADE_TIME.minusMinutes(30))).thenReturn(false);

        service.persistMatchedSignals(result(
                condition(3L, false), condition(1L, true), condition(2L, true)));

        org.mockito.InOrder order = inOrder(signalRepository);
        order.verify(signalRepository)
                .existsByStockAndSearchConditionAndDetectedAtAfter(
                        stock, one, TRADE_TIME.minusMinutes(30));
        order.verify(signalRepository).save(
                org.mockito.ArgumentMatchers.argThat(signal ->
                        signal.getSearchCondition() == one));
        order.verify(signalRepository)
                .existsByStockAndSearchConditionAndDetectedAtAfter(
                        stock, two, TRADE_TIME.minusMinutes(30));
        order.verify(signalRepository).save(
                org.mockito.ArgumentMatchers.argThat(signal ->
                        signal.getSearchCondition() == two));
    }

    @Test
    void skipsSameStockAndConditionInsideCooldown() {
        SearchCondition condition = conditionEntity(1L);
        prepareStockAndConditions(List.of(1L), List.of(condition));
        when(signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, condition, TRADE_TIME.minusMinutes(30)))
                .thenReturn(true);

        service.persistMatchedSignals(result(condition(1L, true)));

        verify(signalRepository, never()).save(
                org.mockito.ArgumentMatchers.any(Signal.class));
    }

    @Test
    void existingSignalForOneConditionDoesNotBlockAnotherCondition() {
        SearchCondition one = conditionEntity(1L);
        SearchCondition two = conditionEntity(2L);
        prepareStockAndConditions(List.of(1L, 2L), List.of(one, two));
        when(signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, one, TRADE_TIME.minusMinutes(30))).thenReturn(true);
        when(signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, two, TRADE_TIME.minusMinutes(30))).thenReturn(false);

        service.persistMatchedSignals(result(
                condition(1L, true), condition(2L, true)));

        verify(signalRepository).save(
                org.mockito.ArgumentMatchers.argThat(signal ->
                        signal.getSearchCondition() == two));
        verify(signalRepository, never()).save(
                org.mockito.ArgumentMatchers.argThat(signal ->
                        signal.getSearchCondition() == one));
    }

    @Test
    void savesOutsideCooldownWhenRepositoryReportsNoRecentSignal() {
        SearchCondition condition = conditionEntity(1L);
        prepareStockAndConditions(List.of(1L), List.of(condition));
        when(signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, condition, TRADE_TIME.minusMinutes(30)))
                .thenReturn(false);

        service.persistMatchedSignals(result(condition(1L, true)));

        verify(signalRepository).existsByStockAndSearchConditionAndDetectedAtAfter(
                stock, condition, TRADE_TIME.minusMinutes(30));
        verify(signalRepository).save(org.mockito.ArgumentMatchers.any(Signal.class));
    }

    @Test
    void rejectsMissingDeletedDisabledOrRealtimeDisabledConditionBeforeSaving() {
        prepareStockAndConditions(List.of(1L), List.of());

        assertThatThrownBy(() -> service.persistMatchedSignals(
                result(condition(1L, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
        verify(signalRepository, never()).save(
                org.mockito.ArgumentMatchers.any(Signal.class));
    }

    private void prepareStockAndConditions(
            List<Long> conditionIds,
            List<SearchCondition> conditions) {
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock));
        when(stock.getStockCode()).thenReturn("005930");
        when(conditionRepository
                .findAllByIdInAndEnabledTrueAndRealtimeEnabledTrueAndDeletedAtIsNull(
                        conditionIds))
                .thenReturn(conditions);
    }

    private SearchCondition conditionEntity(Long id) {
        SearchCondition condition = mock(SearchCondition.class);
        when(condition.getId()).thenReturn(id);
        return condition;
    }

    private RealtimeSignalConditionResult condition(Long id, boolean matched) {
        return new RealtimeSignalConditionResult(id, matched);
    }

    private RealtimeSignalEvaluationResult result(
            RealtimeSignalConditionResult... conditions) {
        return new RealtimeSignalEvaluationResult(
                10L, "005930", TRADE_TIME, List.of(conditions));
    }
}
