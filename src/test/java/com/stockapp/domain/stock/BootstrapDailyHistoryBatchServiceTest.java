package com.stockapp.domain.stock;

import com.stockapp.domain.screening.OperationalScreeningEvaluationDateResolver;
import com.stockapp.domain.screening.OperationalScreeningReadinessResult;
import com.stockapp.domain.screening.metric.OperationalDailyHistoryRequirement;
import com.stockapp.domain.screening.metric.OperationalDailyHistoryRequirementAnalyzer;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillFailure;
import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillResult;
import com.stockapp.domain.stock.dto.KisDailyPriceRequestChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapDailyHistoryBatchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);
    private static final LocalDate EVALUATION_DATE = LocalDate.of(2026, 8, 24);
    private static final List<LocalDate> REQUIRED_DATES = List.of(
            LocalDate.of(2026, 8, 20),
            LocalDate.of(2026, 8, 21));

    @Mock OperationalScreeningEvaluationDateResolver evaluationDateResolver;
    @Mock OperationalDailyHistoryRequirementAnalyzer requirementAnalyzer;
    @Mock KrxTradingCalendar tradingCalendar;
    @Mock StockRepository stockRepository;
    @Mock BootstrapMissingHistoryFetchFillService fetchFillService;

    private BootstrapDailyHistoryBatchService service;
    private Stock first;
    private Stock second;
    private Stock third;

    @BeforeEach
    void setUp() {
        service = new BootstrapDailyHistoryBatchService(
                evaluationDateResolver,
                requirementAnalyzer,
                tradingCalendar,
                stockRepository,
                fetchFillService);
        first = stock(1L, "005930", MarketType.KOSPI);
        second = stock(2L, "000660", MarketType.KOSPI);
        third = stock(3L, "035420", MarketType.KOSDAQ);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void aggregatesCompletedStocksInRepositoryOrderAndIsReady() {
        prepare(2, List.of(first, second, third));
        when(fetchFillService.fetchAndFill(first, REQUIRED_DATES))
                .thenReturn(result(first, BootstrapMissingHistoryFetchFillStatus.COMPLETED,
                        2, 0, 1, 1, 1, 1, 2, 0, 0, 0));
        when(fetchFillService.fetchAndFill(second, REQUIRED_DATES))
                .thenReturn(result(second, BootstrapMissingHistoryFetchFillStatus.COMPLETED,
                        1, 0, 1, 1, 1, 2, 1, 1, 0, 0));
        when(fetchFillService.fetchAndFill(third, REQUIRED_DATES))
                .thenReturn(result(third, BootstrapMissingHistoryFetchFillStatus.COMPLETED,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        BootstrapDailyHistoryBatchResult result = service.bootstrap();

        assertThat(result.status())
                .isEqualTo(BootstrapDailyHistoryBatchStatus.COMPLETED);
        assertThat(result.ready()).isTrue();
        assertThat(result.evaluationDate()).isEqualTo(EVALUATION_DATE);
        assertThat(result.requiredPreviousTradingDayCount()).isEqualTo(2);
        assertThat(result.requiredTradingDateCount()).isEqualTo(2);
        assertThat(result.targetStockCount()).isEqualTo(3);
        assertThat(result.completedStockCount()).isEqualTo(3);
        assertThat(result.totalInitialMissingCount()).isEqualTo(3);
        assertThat(result.plannedRangeCount()).isEqualTo(2);
        assertThat(result.plannedChunkCount()).isEqualTo(2);
        assertThat(result.attemptedChunkCount()).isEqualTo(2);
        assertThat(result.apiCallCount()).isEqualTo(3);
        assertThat(result.savedRowCount()).isEqualTo(3);
        assertThat(result.skippedRowCount()).isOne();
        assertThat(result.problemStocks()).isEmpty();
        InOrder order = inOrder(fetchFillService);
        order.verify(fetchFillService).fetchAndFill(first, REQUIRED_DATES);
        order.verify(fetchFillService).fetchAndFill(second, REQUIRED_DATES);
        order.verify(fetchFillService).fetchAndFill(third, REQUIRED_DATES);
        verify(requirementAnalyzer).analyze();
        verify(tradingCalendar).previousTradingDays(EVALUATION_DATE, 2);
    }

    @Test
    void aggregatesPartialAndFailedStocksAndKeepsProcessing() {
        prepare(2, List.of(first, second, third));
        when(fetchFillService.fetchAndFill(first, REQUIRED_DATES))
                .thenReturn(result(first, BootstrapMissingHistoryFetchFillStatus.PARTIAL,
                        2, 1, 1, 1, 1, 1, 1, 0, 1, 2));
        when(fetchFillService.fetchAndFill(second, REQUIRED_DATES))
                .thenReturn(result(second, BootstrapMissingHistoryFetchFillStatus.FAILED,
                        2, 2, 1, 2, 1, 3, 1, 1, 0, 1));
        when(fetchFillService.fetchAndFill(third, REQUIRED_DATES))
                .thenReturn(result(third, BootstrapMissingHistoryFetchFillStatus.COMPLETED,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        BootstrapDailyHistoryBatchResult result = service.bootstrap();

        assertThat(result.status())
                .isEqualTo(BootstrapDailyHistoryBatchStatus.COMPLETED_WITH_GAPS);
        assertThat(result.ready()).isFalse();
        assertThat(result.completedStockCount()).isOne();
        assertThat(result.partialStockCount()).isOne();
        assertThat(result.failedStockCount()).isOne();
        assertThat(result.totalInitialMissingCount()).isEqualTo(4);
        assertThat(result.totalRemainingMissingCount()).isEqualTo(3);
        assertThat(result.plannedRangeCount()).isEqualTo(2);
        assertThat(result.plannedChunkCount()).isEqualTo(3);
        assertThat(result.attemptedChunkCount()).isEqualTo(2);
        assertThat(result.apiCallCount()).isEqualTo(4);
        assertThat(result.savedRowCount()).isEqualTo(2);
        assertThat(result.skippedRowCount()).isOne();
        assertThat(result.emptyResponseChunkCount()).isOne();
        assertThat(result.outOfRangeResponseRowCount()).isEqualTo(3);
        assertThat(result.problemStocks())
                .extracting(summary -> summary.stockCode())
                .containsExactly("005930", "000660");
        assertThat(result.problemStocks().get(1).failure()).isNotNull();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(result.problemStocks()::clear);
        verify(fetchFillService).fetchAndFill(third, REQUIRED_DATES);
    }

    @Test
    void authenticationFailureStopsBeforeNextStock() {
        prepare(2, List.of(first, second));
        HttpClientErrorException unauthorized =
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        when(fetchFillService.fetchAndFill(first, REQUIRED_DATES))
                .thenThrow(unauthorized);

        assertThatExceptionOfType(HttpClientErrorException.class)
                .isThrownBy(service::bootstrap)
                .isSameAs(unauthorized);
        verify(fetchFillService, never()).fetchAndFill(second, REQUIRED_DATES);
    }

    @Test
    void interruptStopsBeforeNextStockAndPreservesFlag() {
        prepare(2, List.of(first, second));
        KisDailyPriceRequestInterruptedException interrupted =
                new KisDailyPriceRequestInterruptedException(
                        new InterruptedException("stop"));
        Thread.currentThread().interrupt();
        when(fetchFillService.fetchAndFill(first, REQUIRED_DATES))
                .thenThrow(interrupted);

        assertThatExceptionOfType(KisDailyPriceRequestInterruptedException.class)
                .isThrownBy(service::bootstrap)
                .isSameAs(interrupted);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(fetchFillService, never()).fetchAndFill(second, REQUIRED_DATES);
    }

    @Test
    void analyzesRequirementAndCalendarOnlyOnceForAllStocks() {
        prepare(2, List.of(first, second));
        when(fetchFillService.fetchAndFill(first, REQUIRED_DATES))
                .thenReturn(completed(first));
        when(fetchFillService.fetchAndFill(second, REQUIRED_DATES))
                .thenReturn(completed(second));

        service.bootstrap();

        verify(requirementAnalyzer, times(1)).analyze();
        verify(tradingCalendar, times(1))
                .previousTradingDays(EVALUATION_DATE, 2);
    }

    @Test
    void zeroPreviousRequirementCompletesUniverseWithoutCalendarOrFetch() {
        when(evaluationDateResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.ready(
                        TODAY, EVALUATION_DATE));
        when(requirementAnalyzer.analyze()).thenReturn(requirement(0));
        when(stockRepository.findByMarketTypeInOrderByIdAsc(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(List.of(first, second));

        BootstrapDailyHistoryBatchResult result = service.bootstrap();

        assertThat(result.status())
                .isEqualTo(BootstrapDailyHistoryBatchStatus.COMPLETED);
        assertThat(result.targetStockCount()).isEqualTo(2);
        assertThat(result.completedStockCount()).isEqualTo(2);
        assertThat(result.requiredTradingDateCount()).isZero();
        assertThat(result.ready()).isTrue();
        verifyNoInteractions(tradingCalendar, fetchFillService);
    }

    @Test
    void emptyOperationalUniverseFailsClosed() {
        when(evaluationDateResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.ready(
                        TODAY, EVALUATION_DATE));
        when(requirementAnalyzer.analyze()).thenReturn(requirement(2));
        when(stockRepository.findByMarketTypeInOrderByIdAsc(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(List.of());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(service::bootstrap)
                .withMessage("bootstrap target universe became empty");
        verifyNoInteractions(tradingCalendar, fetchFillService);
    }

    @Test
    void nonTradingDayFailsClosedWithoutInventingEvaluationDate() {
        when(evaluationDateResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.notTradingDay(TODAY));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(service::bootstrap)
                .withMessageContaining("evaluation date is unavailable");
        verifyNoInteractions(requirementAnalyzer, tradingCalendar,
                stockRepository, fetchFillService);
    }

    @Test
    void finalizationNotReadyStillUsesResolversExpectedEvaluationDate() {
        when(evaluationDateResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.finalizationNotReady(
                        TODAY, EVALUATION_DATE));
        when(requirementAnalyzer.analyze()).thenReturn(requirement(0));
        when(stockRepository.findByMarketTypeInOrderByIdAsc(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(List.of(first));

        BootstrapDailyHistoryBatchResult result = service.bootstrap();

        assertThat(result.evaluationDate()).isEqualTo(EVALUATION_DATE);
        assertThat(result.completedStockCount()).isOne();
    }

    private void prepare(int count, List<Stock> stocks) {
        when(evaluationDateResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.ready(
                        TODAY, EVALUATION_DATE));
        when(requirementAnalyzer.analyze()).thenReturn(requirement(count));
        when(stockRepository.findByMarketTypeInOrderByIdAsc(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(stocks);
        when(tradingCalendar.previousTradingDays(EVALUATION_DATE, count))
                .thenReturn(REQUIRED_DATES);
    }

    private OperationalDailyHistoryRequirement requirement(int count) {
        return new OperationalDailyHistoryRequirement(
                0, count, 0, 0, count, true);
    }

    private BootstrapMissingHistoryFetchFillResult completed(Stock stock) {
        return result(stock, BootstrapMissingHistoryFetchFillStatus.COMPLETED,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private BootstrapMissingHistoryFetchFillResult result(
            Stock stock,
            BootstrapMissingHistoryFetchFillStatus status,
            int initialMissing,
            int remainingMissing,
            int ranges,
            int chunks,
            int attempted,
            int apiCalls,
            int saved,
            int skipped,
            int empty,
            int outOfRange
    ) {
        BootstrapMissingHistoryFetchFillFailure failure =
                status == BootstrapMissingHistoryFetchFillStatus.FAILED
                        ? new BootstrapMissingHistoryFetchFillFailure(
                                new KisDailyPriceRequestChunk(
                                        REQUIRED_DATES.getFirst(),
                                        REQUIRED_DATES.getLast()),
                                "ResourceAccessException", "network", 2)
                        : null;
        return new BootstrapMissingHistoryFetchFillResult(
                status,
                stock.getStockCode(),
                initialMissing,
                ranges,
                chunks,
                attempted,
                apiCalls,
                saved,
                skipped,
                empty,
                outOfRange,
                REQUIRED_DATES.subList(0, remainingMissing),
                failure);
    }

    private Stock stock(Long id, String stockCode, MarketType marketType) {
        return Stock.builder()
                .id(id)
                .stockCode(stockCode)
                .stockName(stockCode)
                .marketType(marketType)
                .build();
    }
}
