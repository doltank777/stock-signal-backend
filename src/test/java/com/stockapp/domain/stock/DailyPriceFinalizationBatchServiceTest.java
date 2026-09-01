package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceCompletenessResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationBatchResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationResult;
import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPriceFinalizationBatchServiceTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 20);
    private static final Instant STARTED_AT = Instant.parse("2026-08-20T07:20:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-08-20T08:10:00Z");

    @Mock OperationalStockUniverseService stockUniverseService;
    @Mock DailyPriceFinalizationService finalizationService;
    @Mock DailyPriceCompletenessEvaluator completenessEvaluator;
    @Mock DailyPriceLoadSleeper sleeper;
    @Mock Clock clock;
    private DailyPriceFinalizationRunGuard runGuard;

    private KisProperties properties;
    private DailyPriceFinalizationBatchService service;
    private Stock a;
    private Stock b;
    private Stock c;

    @BeforeEach
    void setUp() {
        properties = new KisProperties();
        properties.getDailyPrice().getUpdate().setRequestDelayMs(0);
        properties.getDailyPrice().getUpdate().getRetry().setInitialBackoffMs(0);
        runGuard = new DailyPriceFinalizationRunGuard();
        service = new DailyPriceFinalizationBatchService(
                stockUniverseService, finalizationService, completenessEvaluator,
                properties, sleeper, clock, runGuard);
        a = stock(1L, "000001", MarketType.KOSPI);
        b = stock(2L, "000002", MarketType.KOSDAQ);
        c = stock(3L, "000003", MarketType.KOSPI);
    }

    @Test
    void aggregatesAllSuccessStatusesInIdOrder() {
        prepareStocks();
        when(finalizationService.finalizeStock(a, TARGET_DATE))
                .thenReturn(result(a, DailyPriceFinalizationStatus.INSERTED));
        when(finalizationService.finalizeStock(b, TARGET_DATE))
                .thenReturn(result(b, DailyPriceFinalizationStatus.UPDATED));
        when(finalizationService.finalizeStock(c, TARGET_DATE))
                .thenReturn(result(c, DailyPriceFinalizationStatus.UNCHANGED));
        DailyPriceCompletenessResult completeness = completeness(true);
        when(completenessEvaluator.evaluate(
                org.mockito.ArgumentMatchers.any())).thenReturn(completeness);

        DailyPriceFinalizationExecutionResult execution =
                service.finalizeAll(TARGET_DATE);

        DailyPriceFinalizationBatchResult batch = execution.batch();
        assertThat(batch.targetStockCount()).isEqualTo(3);
        assertThat(batch.insertedStockCount()).isEqualTo(1);
        assertThat(batch.updatedStockCount()).isEqualTo(1);
        assertThat(batch.unchangedStockCount()).isEqualTo(1);
        assertThat(batch.noDataStockCount()).isZero();
        assertThat(batch.failedStockCount()).isZero();
        assertThat(batch.apiCallCount()).isEqualTo(3);
        assertThat(batch.completed()).isTrue();
        assertThat(batch.startedAt()).isEqualTo(STARTED_AT);
        assertThat(batch.finishedAt()).isEqualTo(FINISHED_AT);
        assertThat(execution.completeness()).isSameAs(completeness);
        InOrder order = inOrder(finalizationService);
        order.verify(finalizationService).finalizeStock(a, TARGET_DATE);
        order.verify(finalizationService).finalizeStock(b, TARGET_DATE);
        order.verify(finalizationService).finalizeStock(c, TARGET_DATE);
    }

    @Test
    void keepsNoDataSeparateAndReturnsCompletedBatch() {
        prepareStocks();
        when(finalizationService.finalizeStock(a, TARGET_DATE))
                .thenReturn(result(a, DailyPriceFinalizationStatus.INSERTED));
        when(finalizationService.finalizeStock(b, TARGET_DATE))
                .thenReturn(result(b, DailyPriceFinalizationStatus.NO_DATA));
        when(finalizationService.finalizeStock(c, TARGET_DATE))
                .thenReturn(result(c, DailyPriceFinalizationStatus.UNCHANGED));
        when(completenessEvaluator.evaluate(
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(completeness(false));

        DailyPriceFinalizationBatchResult batch =
                service.finalizeAll(TARGET_DATE).batch();

        assertThat(batch.completed()).isTrue();
        assertThat(batch.noDataStockCount()).isEqualTo(1);
        assertThat(batch.failedStockCount()).isZero();
        assertThat(batch.noDataStockCodes()).containsExactly("000002");
    }

    @Test
    void continuesAfterOrdinaryFailureAndRecordsIt() {
        prepareStocks();
        when(finalizationService.finalizeStock(a, TARGET_DATE))
                .thenReturn(result(a, DailyPriceFinalizationStatus.INSERTED));
        when(finalizationService.finalizeStock(b, TARGET_DATE))
                .thenThrow(new IllegalArgumentException("bad response"));
        when(finalizationService.finalizeStock(c, TARGET_DATE))
                .thenReturn(result(c, DailyPriceFinalizationStatus.UPDATED));
        when(completenessEvaluator.evaluate(
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(completeness(false));

        DailyPriceFinalizationBatchResult batch =
                service.finalizeAll(TARGET_DATE).batch();

        assertThat(batch.insertedStockCount()).isEqualTo(1);
        assertThat(batch.updatedStockCount()).isEqualTo(1);
        assertThat(batch.failedStockCount()).isEqualTo(1);
        assertThat(batch.failedStocks()).extracting(failure -> failure.stockCode())
                .containsExactly("000002");
        verify(finalizationService).finalizeStock(c, TARGET_DATE);
    }

    @Test
    void retriesOnlyFailedStocksOnceInSecondPassAndUsesFinalStatus() {
        prepareStocks();
        when(finalizationService.finalizeStock(a, TARGET_DATE))
                .thenReturn(result(a, DailyPriceFinalizationStatus.INSERTED));
        when(finalizationService.finalizeStock(b, TARGET_DATE))
                .thenThrow(new IllegalArgumentException("transient"))
                .thenReturn(result(b, DailyPriceFinalizationStatus.UPDATED));
        when(finalizationService.finalizeStock(c, TARGET_DATE))
                .thenReturn(result(c, DailyPriceFinalizationStatus.NO_DATA));
        when(completenessEvaluator.evaluate(
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(completeness(false));

        DailyPriceFinalizationBatchResult batch =
                service.finalizeAll(TARGET_DATE).batch();

        assertThat(batch.failedStockCount()).isZero();
        assertThat(batch.updatedStockCount()).isEqualTo(1);
        assertThat(batch.noDataStockCount()).isEqualTo(1);
        assertThat(batch.apiCallCount()).isEqualTo(4);
        verify(finalizationService, times(2)).finalizeStock(b, TARGET_DATE);
        verify(finalizationService, times(1)).finalizeStock(c, TARGET_DATE);
    }

    @Test
    void authenticationFailureAbortsBatch() {
        prepareStocks();
        HttpClientErrorException failure =
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        when(finalizationService.finalizeStock(a, TARGET_DATE))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.finalizeAll(TARGET_DATE))
                .isSameAs(failure);
        verify(finalizationService, never()).finalizeStock(b, TARGET_DATE);
        verify(completenessEvaluator, never()).evaluate(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void interruptDuringPacingStopsBatchAndRestoresFlag() throws Exception {
        properties.getDailyPrice().getUpdate().setRequestDelayMs(1);
        prepareStocks();
        when(finalizationService.finalizeStock(a, TARGET_DATE))
                .thenReturn(result(a, DailyPriceFinalizationStatus.INSERTED));
        org.mockito.Mockito.doThrow(new InterruptedException("interrupted"))
                .when(sleeper).sleep(1L);

        try {
            assertThatThrownBy(() -> service.finalizeAll(TARGET_DATE))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(finalizationService, never()).finalizeStock(b, TARGET_DATE);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void usesHistoryUniverseTargets() {
        when(clock.instant()).thenReturn(STARTED_AT, FINISHED_AT);
        when(stockUniverseService.findHistoryTargets())
                .thenReturn(List.of());
        when(completenessEvaluator.evaluate(
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(completeness(true));

        service.finalizeAll(TARGET_DATE);

        verify(stockUniverseService).findHistoryTargets();
    }

    @Test
    void completenessUsesTheSameHistoryUniverseSnapshotAsTheBatch() {
        Stock suspended = historyStock(4L, "000004", true, false);
        Stock liquidation = historyStock(5L, "000005", false, true);
        when(clock.instant()).thenReturn(STARTED_AT, FINISHED_AT);
        when(stockUniverseService.findHistoryTargets())
                .thenReturn(List.of(suspended, liquidation));
        when(finalizationService.finalizeStock(suspended, TARGET_DATE))
                .thenReturn(result(
                        suspended, DailyPriceFinalizationStatus.UNCHANGED));
        when(finalizationService.finalizeStock(liquidation, TARGET_DATE))
                .thenReturn(result(
                        liquidation, DailyPriceFinalizationStatus.UNCHANGED));
        when(completenessEvaluator.evaluate(
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(completeness(true));

        DailyPriceFinalizationExecutionResult execution =
                service.finalizeAll(TARGET_DATE);

        ArgumentCaptor<DailyPriceFinalizationBatchResult> captor =
                ArgumentCaptor.forClass(DailyPriceFinalizationBatchResult.class);
        verify(completenessEvaluator).evaluate(captor.capture());
        assertThat(captor.getValue()).isSameAs(execution.batch());
        assertThat(captor.getValue().targetStocks())
                .extracting(target -> target.stockCode())
                .containsExactly("000004", "000005");
        assertThat(captor.getValue().targetStockCount()).isEqualTo(2);
        assertThat(execution.completeness().ready()).isTrue();
    }

    private void prepareStocks() {
        when(clock.instant()).thenReturn(STARTED_AT, FINISHED_AT);
        when(stockUniverseService.findHistoryTargets())
                .thenReturn(List.of(a, b, c));
    }

    private DailyPriceFinalizationResult result(
            Stock stock,
            DailyPriceFinalizationStatus status
    ) {
        return new DailyPriceFinalizationResult(
                stock.getStockCode(), TARGET_DATE, status);
    }

    private DailyPriceCompletenessResult completeness(boolean ready) {
        return new DailyPriceCompletenessResult(
                TARGET_DATE, 0, 0, 0, 0, ready,
                List.of(), List.of(), List.of());
    }

    private Stock stock(long id, String code, MarketType marketType) {
        return Stock.builder().id(id).stockCode(code).stockName(code)
                .marketType(marketType).build();
    }

    private Stock historyStock(
            long id,
            String code,
            boolean suspended,
            boolean liquidationTrading
    ) {
        return Stock.builder().id(id).stockCode(code).stockName(code)
                .marketType(MarketType.KOSPI)
                .presentInLatestMaster(true)
                .instrumentType(InstrumentType.COMMON_STOCK)
                .suspended(suspended)
                .liquidationTrading(liquidationTrading)
                .build();
    }
}
