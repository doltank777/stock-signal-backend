package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillResult;
import com.stockapp.domain.stock.dto.DailyHistoryGap;
import com.stockapp.domain.stock.dto.DailyHistoryMissingRange;
import com.stockapp.domain.stock.dto.KisDailyPriceRequestChunk;
import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class BootstrapMissingHistoryFetchFillServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 3, 27);
    private static final LocalDate D2 = LocalDate.of(2026, 3, 30);
    private static final LocalDate D3 = LocalDate.of(2026, 3, 31);

    @Mock DailyHistoryGapDetector gapDetector;
    @Mock DailyHistoryMissingRangePlanner missingRangePlanner;
    @Mock KisDailyPriceRequestChunkPlanner chunkPlanner;
    @Mock KisDailyPriceClient dailyPriceClient;
    @Mock StockDailyPriceWriter dailyPriceWriter;
    @Mock DailyPriceLoadSleeper sleeper;

    private Stock stock;
    private KisProperties properties;
    private BootstrapMissingHistoryFetchFillService service;

    @BeforeEach
    void setUp() {
        stock = Stock.builder().stockCode("005930").stockName("Samsung")
                .marketType(MarketType.KOSPI).build();
        properties = new KisProperties();
        properties.getDailyPrice().getUpdate().setRequestDelayMs(0);
        properties.getDailyPrice().getUpdate().getRetry().setMaxAttempts(2);
        properties.getDailyPrice().getUpdate().getRetry().setInitialBackoffMs(0);
        properties.getDailyPrice().getUpdate().getRetry().setMultiplier(2);
        service = new BootstrapMissingHistoryFetchFillService(
                gapDetector,
                missingRangePlanner,
                chunkPlanner,
                new KisDailyPriceRequestExecutor(sleeper),
                dailyPriceClient,
                dailyPriceWriter,
                sleeper,
                properties);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void returnsCompletedWithoutPlanningOrRequestWhenAlreadyComplete() {
        List<LocalDate> required = List.of(D1, D2);
        when(gapDetector.detect(stock, required))
                .thenReturn(new DailyHistoryGap(List.of()));

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.COMPLETED);
        assertThat(result.initialMissingCount()).isZero();
        assertThat(result.plannedChunkCount()).isZero();
        assertThat(result.attemptedChunkCount()).isZero();
        assertThat(result.apiCallCount()).isZero();
        assertThat(result.remainingMissingDates()).isEmpty();
        verifyNoInteractions(missingRangePlanner, chunkPlanner,
                dailyPriceClient, dailyPriceWriter, sleeper);
    }

    @Test
    void fillsSingleChunkAndCompletesFromFinalDatabaseGap() {
        List<LocalDate> required = List.of(D1, D2);
        KisDailyPriceRequestChunk chunk = plan(required, D1, D2);
        when(dailyPriceClient.getDailyPrices("005930", D1, D2))
                .thenReturn(List.of(price(D2), price(D1)));
        when(dailyPriceWriter.write(any(), any())).thenReturn(saved(2, 0));
        finalGap(required, List.of());

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.COMPLETED);
        assertThat(result.savedRowCount()).isEqualTo(2);
        assertThat(result.apiCallCount()).isOne();
        assertThat(result.attemptedChunkCount()).isOne();
        assertThat(result.failure()).isNull();
        verify(dailyPriceClient).getDailyPrices(
                "005930", chunk.startDate(), chunk.endDate());
        ArgumentCaptor<List<KisDailyPrice>> rows = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceWriter).write(any(), rows.capture());
        assertThat(rows.getValue()).extracting(KisDailyPrice::getTradeDate)
                .containsExactly(D1, D2);
    }

    @Test
    void executesOneHundredAndRemainderChunksOldestFirst() {
        List<LocalDate> required = IntStream.range(0, 101)
                .mapToObj(D1::plusDays)
                .toList();
        LocalDate hundredth = required.get(99);
        LocalDate hundredFirst = required.get(100);
        DailyHistoryMissingRange range = initialGapAndRange(
                required, required.getFirst(), required.getLast());
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.tradingDaysBetween(range.startDate(), range.endDate()))
                .thenReturn(required);
        service = new BootstrapMissingHistoryFetchFillService(
                gapDetector,
                missingRangePlanner,
                new KisDailyPriceRequestChunkPlanner(calendar),
                new KisDailyPriceRequestExecutor(sleeper),
                dailyPriceClient,
                dailyPriceWriter,
                sleeper,
                properties);
        when(dailyPriceClient.getDailyPrices("005930", D1, hundredth))
                .thenReturn(List.of(price(D1), price(hundredth)));
        when(dailyPriceClient.getDailyPrices(
                "005930", hundredFirst, hundredFirst))
                .thenReturn(List.of(price(hundredFirst)));
        when(dailyPriceWriter.write(any(), any()))
                .thenReturn(saved(2, 0), saved(1, 0));
        finalGap(required, List.of());

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.plannedChunkCount()).isEqualTo(2);
        assertThat(result.attemptedChunkCount()).isEqualTo(2);
        InOrder order = inOrder(dailyPriceClient);
        order.verify(dailyPriceClient).getDailyPrices("005930", D1, hundredth);
        order.verify(dailyPriceClient).getDailyPrices(
                "005930", hundredFirst, hundredFirst);
    }

    @Test
    void executesMultipleMissingRangesOldestToNewest() {
        List<LocalDate> required = List.of(D1, D2, D3);
        List<LocalDate> missing = List.of(D1, D3);
        DailyHistoryMissingRange firstRange =
                new DailyHistoryMissingRange(D1, D1);
        DailyHistoryMissingRange secondRange =
                new DailyHistoryMissingRange(D3, D3);
        when(gapDetector.detect(stock, required))
                .thenReturn(new DailyHistoryGap(missing),
                        new DailyHistoryGap(List.of()));
        when(missingRangePlanner.plan(missing))
                .thenReturn(List.of(firstRange, secondRange));
        when(chunkPlanner.plan(firstRange)).thenReturn(List.of(
                new KisDailyPriceRequestChunk(D1, D1)));
        when(chunkPlanner.plan(secondRange)).thenReturn(List.of(
                new KisDailyPriceRequestChunk(D3, D3)));
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenReturn(List.of(price(D1)));
        when(dailyPriceClient.getDailyPrices("005930", D3, D3))
                .thenReturn(List.of(price(D3)));
        when(dailyPriceWriter.write(any(), any()))
                .thenReturn(saved(1, 0), saved(1, 0));

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.plannedRangeCount()).isEqualTo(2);
        assertThat(result.plannedChunkCount()).isEqualTo(2);
        InOrder order = inOrder(dailyPriceClient);
        order.verify(dailyPriceClient).getDailyPrices("005930", D1, D1);
        order.verify(dailyPriceClient).getDailyPrices("005930", D3, D3);
    }

    @Test
    void filtersOutOfRangeRowsAndCountsThem() {
        List<LocalDate> required = List.of(D1, D2);
        plan(required, D1, D2);
        LocalDate outside = D1.minusDays(1);
        when(dailyPriceClient.getDailyPrices("005930", D1, D2))
                .thenReturn(List.of(price(D2), price(outside), price(D1)));
        when(dailyPriceWriter.write(any(), any())).thenReturn(saved(2, 0));
        finalGap(required, List.of());

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.outOfRangeResponseRowCount()).isOne();
        ArgumentCaptor<List<KisDailyPrice>> rows = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceWriter).write(any(), rows.capture());
        assertThat(rows.getValue()).extracting(KisDailyPrice::getTradeDate)
                .containsExactly(D1, D2);
    }

    @Test
    void allOutOfRangeResponseIsNotReportedAsEmptyKisResponse() {
        List<LocalDate> required = List.of(D1);
        plan(required, D1, D1);
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenReturn(List.of(price(D1.minusDays(1))));
        finalGap(required, required);

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.PARTIAL);
        assertThat(result.emptyResponseChunkCount()).isZero();
        assertThat(result.outOfRangeResponseRowCount()).isOne();
        verifyNoInteractions(dailyPriceWriter);
    }

    @Test
    void emptyResponseContinuesAndFinalGapMakesResultPartial() {
        List<LocalDate> required = List.of(D1);
        plan(required, D1, D1);
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenReturn(List.of());
        finalGap(required, required);

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.PARTIAL);
        assertThat(result.emptyResponseChunkCount()).isOne();
        assertThat(result.remainingMissingDates()).containsExactly(D1);
        verifyNoInteractions(dailyPriceWriter);
    }

    @Test
    void partialResponseIsSavedAndFinalGapMakesResultPartial() {
        List<LocalDate> required = List.of(D1, D2);
        plan(required, D1, D2);
        when(dailyPriceClient.getDailyPrices("005930", D1, D2))
                .thenReturn(List.of(price(D1)));
        when(dailyPriceWriter.write(any(), any())).thenReturn(saved(1, 0));
        finalGap(required, List.of(D2));

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.PARTIAL);
        assertThat(result.savedRowCount()).isOne();
        assertThat(result.remainingMissingDates()).containsExactly(D2);
    }

    @Test
    void writerSkipStillCompletesWhenFinalDatabaseGapIsEmpty() {
        List<LocalDate> required = List.of(D1);
        plan(required, D1, D1);
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenReturn(List.of(price(D1)));
        when(dailyPriceWriter.write(any(), any())).thenReturn(saved(0, 1));
        finalGap(required, List.of());

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.COMPLETED);
        assertThat(result.savedRowCount()).isZero();
        assertThat(result.skippedRowCount()).isOne();
    }

    @Test
    void retrySuccessCountsEveryActualApiAttempt() {
        List<LocalDate> required = List.of(D1);
        plan(required, D1, D1);
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenThrow(new ResourceAccessException("temporary"))
                .thenReturn(List.of(price(D1)));
        when(dailyPriceWriter.write(any(), any())).thenReturn(saved(1, 0));
        finalGap(required, List.of());

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.COMPLETED);
        assertThat(result.apiCallCount()).isEqualTo(2);
        verify(dailyPriceClient, times(2))
                .getDailyPrices("005930", D1, D1);
    }

    @Test
    void retryExhaustionStopsRemainingChunksAndReturnsFailedAfterRecheck() {
        List<LocalDate> required = List.of(D1, D2);
        DailyHistoryMissingRange range = initialGapAndRange(required, D1, D2);
        when(chunkPlanner.plan(range)).thenReturn(List.of(
                new KisDailyPriceRequestChunk(D1, D1),
                new KisDailyPriceRequestChunk(D2, D2)));
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenThrow(new ResourceAccessException("network"));
        finalGap(required, required);

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.FAILED);
        assertThat(result.attemptedChunkCount()).isOne();
        assertThat(result.apiCallCount()).isEqualTo(2);
        assertThat(result.failure().attemptCount()).isEqualTo(2);
        assertThat(result.failure().exceptionType())
                .isEqualTo("ResourceAccessException");
        verify(dailyPriceClient, never()).getDailyPrices("005930", D2, D2);
    }

    @Test
    void authenticationFailureIsPropagatedWithoutFinalRecheck() {
        List<LocalDate> required = List.of(D1);
        plan(required, D1, D1);
        HttpClientErrorException unauthorized =
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenThrow(unauthorized);

        assertThatExceptionOfType(HttpClientErrorException.class)
                .isThrownBy(() -> service.fetchAndFill(stock, required))
                .isSameAs(unauthorized);
        verify(gapDetector).detect(stock, required);
    }

    @Test
    void pacingInterruptIsPropagatedAndInterruptFlagIsPreserved() throws Exception {
        properties.getDailyPrice().getUpdate().setRequestDelayMs(1);
        List<LocalDate> required = List.of(D1, D2);
        DailyHistoryMissingRange range = initialGapAndRange(required, D1, D2);
        when(chunkPlanner.plan(range)).thenReturn(List.of(
                new KisDailyPriceRequestChunk(D1, D1),
                new KisDailyPriceRequestChunk(D2, D2)));
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenReturn(List.of(price(D1)));
        when(dailyPriceWriter.write(any(), any())).thenReturn(saved(1, 0));
        doThrow(new InterruptedException("stop")).when(sleeper).sleep(1);

        assertThatExceptionOfType(KisDailyPriceRequestInterruptedException.class)
                .isThrownBy(() -> service.fetchAndFill(stock, required));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(dailyPriceClient, never()).getDailyPrices("005930", D2, D2);
    }

    @Test
    void writerFailureReturnsFailedAndPreservesEarlierChunkWrites() {
        List<LocalDate> required = List.of(D1, D2);
        plan(required, D1, D2);
        when(dailyPriceClient.getDailyPrices("005930", D1, D2))
                .thenReturn(List.of(price(D1), price(D2)));
        when(dailyPriceWriter.write(any(), any()))
                .thenThrow(new IllegalStateException("database write failed"));
        finalGap(required, required);

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThat(result.status())
                .isEqualTo(BootstrapMissingHistoryFetchFillStatus.FAILED);
        assertThat(result.failure().exceptionType())
                .isEqualTo("IllegalStateException");
        assertThat(result.remainingMissingDates()).containsExactly(D1, D2);
    }

    @Test
    void resultRemainingDatesAreImmutable() {
        List<LocalDate> required = List.of(D1);
        plan(required, D1, D1);
        when(dailyPriceClient.getDailyPrices("005930", D1, D1))
                .thenReturn(List.of());
        finalGap(required, required);

        BootstrapMissingHistoryFetchFillResult result =
                service.fetchAndFill(stock, required);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(result.remainingMissingDates()::clear);
    }

    private KisDailyPriceRequestChunk plan(
            List<LocalDate> required,
            LocalDate start,
            LocalDate end
    ) {
        DailyHistoryMissingRange range = initialGapAndRange(required, start, end);
        KisDailyPriceRequestChunk chunk =
                new KisDailyPriceRequestChunk(start, end);
        when(chunkPlanner.plan(range)).thenReturn(List.of(chunk));
        return chunk;
    }

    private DailyHistoryMissingRange initialGapAndRange(
            List<LocalDate> required,
            LocalDate start,
            LocalDate end
    ) {
        when(gapDetector.detect(stock, required))
                .thenReturn(new DailyHistoryGap(required));
        DailyHistoryMissingRange range =
                new DailyHistoryMissingRange(start, end);
        when(missingRangePlanner.plan(required)).thenReturn(List.of(range));
        return range;
    }

    private void finalGap(
            List<LocalDate> required,
            List<LocalDate> remaining
    ) {
        when(gapDetector.detect(stock, required))
                .thenReturn(new DailyHistoryGap(required),
                        new DailyHistoryGap(remaining));
    }

    private StockDailyPriceSaveResult saved(int saved, int skipped) {
        return StockDailyPriceSaveResult.builder()
                .requestedCount(saved + skipped)
                .savedCount(saved)
                .skippedCount(skipped)
                .build();
    }

    private KisDailyPrice price(LocalDate date) {
        return KisDailyPrice.builder()
                .tradeDate(date)
                .openPrice(1L)
                .highPrice(1L)
                .lowPrice(1L)
                .closePrice(1L)
                .volume(1L)
                .build();
    }
}
