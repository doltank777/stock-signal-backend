package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceInitialLoadResult;
import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPriceInitialLoaderTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 12);

    @Mock StockRepository stockRepository;
    @Mock StockDailyPriceRepository dailyPriceRepository;
    @Mock StockDailyPriceWriter writer;
    @Mock KisDailyPriceClient client;
    @Mock DailyPriceLoadSleeper sleeper;

    private KisProperties properties;
    private DailyPriceInitialLoader loader;
    private Stock kospi;
    private Stock kosdaq;

    @BeforeEach
    void setUp() {
        properties = new KisProperties();
        properties.setBaseUrl("https://example.com");
        properties.setAppKey("key");
        properties.setAppSecret("secret");
        properties.getDailyPrice().setTargetTradingDays(3);
        properties.getDailyPrice().setRequestDelayMs(0);
        properties.getDailyPrice().setRetryInitialDelayMs(0);
        properties.getDailyPrice().setProgressLogInterval(1);
        loader = new DailyPriceInitialLoader(
                stockRepository, dailyPriceRepository, writer, client,
                properties, sleeper,
                Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC));
        kospi = stock(1L, "005930", MarketType.KOSPI);
        kosdaq = stock(2L, "035720", MarketType.KOSDAQ);
    }

    @Test
    void selectsOnlyKospiAndKosdaqInIdOrderAndSkipsCompletedStock() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of(kospi, kosdaq));
        when(dailyPriceRepository.countByStockAndTradeDateLessThanEqual(
                kospi, BASE_DATE)).thenReturn(3L);
        when(dailyPriceRepository.countByStockAndTradeDateLessThanEqual(
                kosdaq, BASE_DATE)).thenReturn(3L);

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        ArgumentCaptor<List<MarketType>> markets = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).findByMarketTypeInOrderByIdAsc(markets.capture());
        assertThat(markets.getValue()).containsExactly(MarketType.KOSPI, MarketType.KOSDAQ);
        assertThat(result.getSkippedStockCount()).isEqualTo(2);
        verify(client, never()).getDailyPrices(any(), any(), any());
    }

    @Test
    void configuredStockCodeProcessesOnlyMatchingTargetMarketStock() {
        when(stockRepository.findByStockCodeAndMarketTypeIn(any(), anyList()))
                .thenReturn(Optional.of(kospi));
        when(dailyPriceRepository.countByStockAndTradeDateLessThanEqual(
                kospi, BASE_DATE)).thenReturn(3L);

        DailyPriceInitialLoadResult result = loader.loadStock(" 005930 ", BASE_DATE);

        ArgumentCaptor<List<MarketType>> markets = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).findByStockCodeAndMarketTypeIn(
                org.mockito.ArgumentMatchers.eq("005930"), markets.capture());
        assertThat(markets.getValue()).containsExactly(MarketType.KOSPI, MarketType.KOSDAQ);
        verify(stockRepository, never()).findByMarketTypeInOrderByIdAsc(anyList());
        assertThat(result.getTargetStockCount()).isEqualTo(1);
        assertThat(result.getSkippedStockCount()).isEqualTo(1);
    }

    @Test
    void blankStockCodeKeepsFullTargetSelection() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of());

        DailyPriceInitialLoadResult result = loader.loadStock("  ", BASE_DATE);

        verify(stockRepository).findByMarketTypeInOrderByIdAsc(anyList());
        verify(stockRepository, never()).findByStockCodeAndMarketTypeIn(any(), anyList());
        assertThat(result.getTargetStockCount()).isZero();
    }

    @Test
    void unknownStockCodeFailsWithoutFallingBackToAllStocks() {
        when(stockRepository.findByStockCodeAndMarketTypeIn(any(), anyList()))
                .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> loader.loadStock("999999", BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999999");

        verify(stockRepository, never()).findByMarketTypeInOrderByIdAsc(anyList());
        verify(client, never()).getDailyPrices(any(), any(), any());
    }

    @Test
    void konexStockCodeIsExcludedFromInitialLoadTargets() {
        Stock konex = stock(3L, "123456", MarketType.KONEX);
        when(stockRepository.findByStockCodeAndMarketTypeIn(any(), anyList()))
                .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> loader.loadStock(konex.getStockCode(), BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(konex.getStockCode());

        ArgumentCaptor<List<MarketType>> markets = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).findByStockCodeAndMarketTypeIn(
                org.mockito.ArgumentMatchers.eq(konex.getStockCode()), markets.capture());
        assertThat(markets.getValue()).doesNotContain(MarketType.KONEX);
        verify(client, never()).getDailyPrices(any(), any(), any());
    }

    @Test
    void partialDataRestartsAtBaseDateAndMovesFromActualOldestDate() {
        prepareOneStock(kospi, 1L);
        List<KisDailyPrice> unsorted = List.of(price("2026-08-10"), price("2026-08-12"));
        List<KisDailyPrice> older = List.of(price("2026-08-09"));
        when(client.getDailyPrices(any(), any(), any()))
                .thenReturn(unsorted, older);
        when(writer.write(kospi, unsorted)).thenReturn(save(2, 1));
        when(writer.write(kospi, older)).thenReturn(save(1, 1));

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        ArgumentCaptor<LocalDate> starts = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> ends = ArgumentCaptor.forClass(LocalDate.class);
        verify(client, times(2)).getDailyPrices(any(), starts.capture(), ends.capture());
        assertThat(ends.getAllValues()).containsExactly(BASE_DATE, LocalDate.of(2026, 8, 9));
        assertThat(starts.getAllValues().getFirst()).isEqualTo(BASE_DATE.minusMonths(6));
        assertThat(result.getCompletedStockCount()).isEqualTo(1);
        assertThat(result.getSavedDailyPriceCount()).isEqualTo(2);
    }

    @Test
    void emptyResponseIsPartialHistory() {
        prepareOneStock(kospi, 0L);
        when(client.getDailyPrices(any(), any(), any())).thenReturn(List.of());

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        assertThat(result.getPartialHistoryStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isEqualTo(1);
    }

    @Test
    void maxApiCallsStopsLoop() {
        properties.getDailyPrice().setMaxApiCallsPerStock(1);
        prepareOneStock(kospi, 0L);
        List<KisDailyPrice> response = List.of(price("2026-08-10"));
        when(client.getDailyPrices(any(), any(), any())).thenReturn(response);
        when(writer.write(kospi, response)).thenReturn(save(1, 1));

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        assertThat(result.getPartialHistoryStockCount()).isEqualTo(1);
        verify(client, times(1)).getDailyPrices(any(), any(), any());
    }

    @Test
    void maxLookbackStopsBeforeAnotherRequest() {
        properties.getDailyPrice().setMaxLookbackYears(1);
        properties.getDailyPrice().setRequestWindowMonths(18);
        prepareOneStock(kospi, 0L);
        List<KisDailyPrice> response = List.of(price("2025-08-12"));
        when(client.getDailyPrices(any(), any(), any())).thenReturn(response);
        when(writer.write(kospi, response)).thenReturn(save(1, 1));

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        assertThat(result.getPartialHistoryStockCount()).isEqualTo(1);
        verify(client, times(1)).getDailyPrices(any(), any(), any());
    }

    @Test
    void maxApiCallsAlsoCapsRetries() {
        properties.getDailyPrice().setMaxApiCallsPerStock(1);
        prepareOneStock(kospi, 0L);
        when(client.getDailyPrices(any(), any(), any()))
                .thenThrow(new KisApiException("EGW00201", "rate limit"));

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        assertThat(result.getFailedStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isEqualTo(1);
    }

    @Test
    void retriesTransientFailureAndThenSucceeds() {
        properties.getDailyPrice().setTargetTradingDays(1);
        prepareOneStock(kospi, 0L);
        List<KisDailyPrice> response = List.of(price("2026-08-10"));
        when(client.getDailyPrices(any(), any(), any()))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(response);
        when(writer.write(kospi, response)).thenReturn(save(1, 1));

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        assertThat(result.getCompletedStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isEqualTo(2);
    }

    @Test
    void exhaustedRetryFailsOneStockAndContinuesWithNext() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of(kospi, kosdaq));
        when(dailyPriceRepository.countByStockAndTradeDateLessThanEqual(any(), any()))
                .thenReturn(0L);
        when(client.getDailyPrices(kospi.getStockCode(), BASE_DATE.minusMonths(6), BASE_DATE))
                .thenThrow(new KisApiException("EGW00201", "rate limit"));
        when(client.getDailyPrices(kosdaq.getStockCode(), BASE_DATE.minusMonths(6), BASE_DATE))
                .thenReturn(List.of());

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        assertThat(result.getFailedStockCount()).isEqualTo(1);
        assertThat(result.getPartialHistoryStockCount()).isEqualTo(1);
        assertThat(result.getFailures().getFirst().messageCode()).isEqualTo("EGW00201");
        verify(client, times(3)).getDailyPrices(kospi.getStockCode(),
                BASE_DATE.minusMonths(6), BASE_DATE);
        verify(client).getDailyPrices(kosdaq.getStockCode(),
                BASE_DATE.minusMonths(6), BASE_DATE);
    }

    @Test
    void nonRetryableErrorFailsImmediately() {
        prepareOneStock(kospi, 0L);
        when(client.getDailyPrices(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("invalid data"));

        DailyPriceInitialLoadResult result = loader.load(BASE_DATE);

        assertThat(result.getFailedStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void authenticationHttpFailureAbortsEntireLoad(int statusCode) {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of(kospi, kosdaq));
        when(dailyPriceRepository.countByStockAndTradeDateLessThanEqual(
                kospi, BASE_DATE)).thenReturn(0L);
        HttpClientErrorException failure = HttpClientErrorException.create(
                HttpStatus.valueOf(statusCode), "authentication failed",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        when(client.getDailyPrices(kospi.getStockCode(),
                BASE_DATE.minusMonths(6), BASE_DATE)).thenThrow(failure);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> loader.load(BASE_DATE))
                .isSameAs(failure);

        verify(dailyPriceRepository, never())
                .countByStockAndTradeDateLessThanEqual(kosdaq, BASE_DATE);
        verify(client, never()).getDailyPrices(
                org.mockito.ArgumentMatchers.eq(kosdaq.getStockCode()),
                any(), any());
    }

    @Test
    void interruptDuringRequestDelayAbortsEntireLoadAndRestoresFlag()
            throws Exception {
        properties.getDailyPrice().setRequestDelayMs(1);
        prepareOneStock(kospi, 0L);
        List<KisDailyPrice> response = List.of(price("2026-08-10"));
        when(client.getDailyPrices(any(), any(), any())).thenReturn(response);
        when(writer.write(kospi, response)).thenReturn(save(1, 1));
        org.mockito.Mockito.doThrow(new InterruptedException("interrupted"))
                .when(sleeper).sleep(1L);

        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> loader.load(BASE_DATE))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void runnerUsesDedicatedProfile() {
        Profile profile = DailyPriceInitialLoadRunner.class.getAnnotation(Profile.class);
        assertThat(profile.value()).containsExactly("daily-price-load");
    }

    @Test
    void runnerWithoutOverridesKeepsExistingLoadEntryPoint() {
        DailyPriceInitialLoader runnerLoader = org.mockito.Mockito.mock(
                DailyPriceInitialLoader.class);
        DailyPriceInitialLoadRunner runner = new DailyPriceInitialLoadRunner(
                runnerLoader, "", "");

        runner.run(null);

        verify(runnerLoader).load();
        verify(runnerLoader, never()).loadStock(any());
    }

    @Test
    void runnerPassesConfiguredStockCodeAndBaseDate() {
        DailyPriceInitialLoader runnerLoader = org.mockito.Mockito.mock(
                DailyPriceInitialLoader.class);
        DailyPriceInitialLoadRunner runner = new DailyPriceInitialLoadRunner(
                runnerLoader, BASE_DATE.toString(), "005930");

        runner.run(null);

        verify(runnerLoader).loadStock("005930", BASE_DATE);
        verify(runnerLoader, never()).load(any(LocalDate.class));
    }

    @Test
    void runnerPropagatesLoaderFailure() {
        DailyPriceInitialLoader runnerLoader = org.mockito.Mockito.mock(
                DailyPriceInitialLoader.class);
        DailyPriceInitialLoadRunner runner = new DailyPriceInitialLoadRunner(
                runnerLoader, "", "005930");
        IllegalStateException failure = new IllegalStateException("invalid KIS configuration");
        org.mockito.Mockito.when(runnerLoader.loadStock("005930")).thenThrow(failure);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runner.run(null))
                .isSameAs(failure);

    }

    private void prepareOneStock(Stock stock, long count) {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of(stock));
        when(dailyPriceRepository.countByStockAndTradeDateLessThanEqual(stock, BASE_DATE))
                .thenReturn(count);
    }

    private Stock stock(long id, String code, MarketType market) {
        return Stock.builder().id(id).stockCode(code).stockName(code)
                .marketType(market).build();
    }

    private KisDailyPrice price(String date) {
        return KisDailyPrice.builder().tradeDate(LocalDate.parse(date))
                .openPrice(1L).highPrice(2L).lowPrice(1L).closePrice(2L)
                .volume(10L).build();
    }

    private StockDailyPriceSaveResult save(int requested, int saved) {
        return StockDailyPriceSaveResult.builder().requestedCount(requested)
                .savedCount(saved).skippedCount(requested - saved).build();
    }
}
