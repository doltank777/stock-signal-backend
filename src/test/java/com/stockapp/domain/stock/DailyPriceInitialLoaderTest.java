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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

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

    @Test
    void runnerUsesDedicatedProfile() {
        Profile profile = DailyPriceInitialLoadRunner.class.getAnnotation(Profile.class);
        assertThat(profile.value()).containsExactly("daily-price-load");
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
