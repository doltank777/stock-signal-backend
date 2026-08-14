package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceUpdateResult;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPriceUpdateServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Mock StockRepository stockRepository;
    @Mock StockDailyPriceRepository dailyPriceRepository;
    @Mock StockDailyPriceWriter writer;
    @Mock KisDailyPriceClient client;
    @Mock DailyPriceLoadSleeper sleeper;

    private DailyPriceUpdateService service;
    private KisProperties properties;
    private Stock kospi;
    private Stock kosdaq;

    @BeforeEach
    void setUp() {
        properties = new KisProperties();
        properties.setBaseUrl("https://example.com");
        properties.setAppKey("key");
        properties.setAppSecret("secret");
        properties.getDailyPrice().setProgressLogInterval(25);
        properties.getDailyPrice().getUpdate().setRequestDelayMs(0);
        properties.getDailyPrice().getUpdate().getRetry().setInitialBackoffMs(0);
        service = new DailyPriceUpdateService(
                stockRepository, dailyPriceRepository, writer, client,
                properties, sleeper,
                Clock.fixed(Instant.parse("2026-08-14T08:00:00Z"), ZoneOffset.UTC));
        kospi = stock(1L, "005930", MarketType.KOSPI);
        kosdaq = stock(2L, "035720", MarketType.KOSDAQ);
    }

    @Test
    void savesOneNewDailyPrice() {
        prepareOneStock(kospi, LocalDate.of(2026, 8, 13));
        List<KisDailyPrice> prices = List.of(price("2026-08-14"));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE)).thenReturn(prices);
        when(writer.write(kospi, prices)).thenReturn(save(1));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getUpdatedStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isEqualTo(1);
        assertThat(result.getSavedDailyPriceCount()).isEqualTo(1);
    }

    @Test
    void retriesRateLimitWithExponentialBackoffAndCountsEveryCall() throws Exception {
        properties.getDailyPrice().getUpdate().getRetry().setInitialBackoffMs(100);
        properties.getDailyPrice().getUpdate().getRetry().setMultiplier(2);
        prepareOneStock(kospi, LocalDate.of(2026, 8, 13));
        List<KisDailyPrice> prices = List.of(price("2026-08-14"));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(new KisApiException("EGW00201", "rate limit"))
                .thenThrow(new KisApiException("EGW00201", "rate limit"))
                .thenReturn(prices);
        when(writer.write(kospi, prices)).thenReturn(save(1));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        verify(sleeper).sleep(100);
        verify(sleeper).sleep(200);
        assertThat(result.getApiCallCount()).isEqualTo(3);
        assertThat(result.getUpdatedStockCount()).isEqualTo(1);
    }

    @Test
    void retriesHttp429AndServerError() {
        prepareOneStock(kospi, LocalDate.of(2026, 8, 13));
        List<KisDailyPrice> prices = List.of(price("2026-08-14"));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(prices);
        when(writer.write(kospi, prices)).thenReturn(save(1));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getApiCallCount()).isEqualTo(3);
        assertThat(result.getUpdatedStockCount()).isEqualTo(1);
    }

    @Test
    void retriesResourceAccessFailure() {
        prepareOneStock(kospi, LocalDate.of(2026, 8, 13));
        List<KisDailyPrice> prices = List.of(price("2026-08-14"));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(prices);
        when(writer.write(kospi, prices)).thenReturn(save(1));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getApiCallCount()).isEqualTo(2);
        assertThat(result.getUpdatedStockCount()).isEqualTo(1);
    }

    @Test
    void skipsApiCallWhenAlreadyUpToDate() throws Exception {
        prepareOneStock(kospi, BASE_DATE);

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getUpToDateStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isZero();
        verify(client, never()).getDailyPrices(any(), any(), any());
        verify(writer, never()).write(any(), anyList());
        verify(sleeper, never()).sleep(anyLong());
    }

    @Test
    void catchesUpOnlyRequestedRangeAndSortsByTradeDate() {
        prepareOneStock(kospi, LocalDate.of(2026, 8, 10));
        when(client.getDailyPrices("005930", LocalDate.of(2026, 8, 11), BASE_DATE))
                .thenReturn(List.of(
                        price("2026-08-14"), price("2026-08-10"),
                        price("2026-08-12"), price("2026-08-15"),
                        price("2026-08-11"), price("2026-08-13")));
        when(writer.write(any(), anyList())).thenReturn(save(4));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        ArgumentCaptor<List<KisDailyPrice>> prices = ArgumentCaptor.forClass(List.class);
        verify(writer).write(any(), prices.capture());
        assertThat(prices.getValue()).extracting(KisDailyPrice::getTradeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 14));
        assertThat(result.getSavedDailyPriceCount()).isEqualTo(4);
    }

    @Test
    void emptyFilteredResponseIsNoNewData() {
        prepareOneStock(kospi, LocalDate.of(2026, 8, 13));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenReturn(List.of(price("2026-08-13")));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getNoNewDataStockCount()).isEqualTo(1);
        assertThat(result.getFailedStockCount()).isZero();
        verify(writer, never()).write(any(), anyList());
    }

    @Test
    void noBaseHistorySkipsApiCall() throws Exception {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of(kospi));
        when(dailyPriceRepository.findLatestTradeDateByStock(kospi))
                .thenReturn(Optional.empty());

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getNoBaseHistoryStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isZero();
        verify(client, never()).getDailyPrices(any(), any(), any());
        verify(sleeper, never()).sleep(anyLong());
    }

    @Test
    void kisFailureIsIsolatedAndNextStockContinues() {
        prepareStocks(LocalDate.of(2026, 8, 13));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(new KisApiException("EGW00201", "rate limit"));
        List<KisDailyPrice> prices = List.of(price("2026-08-14"));
        when(client.getDailyPrices("035720", BASE_DATE, BASE_DATE)).thenReturn(prices);
        when(writer.write(kosdaq, prices)).thenReturn(save(1));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getFailedStockCount()).isEqualTo(1);
        assertThat(result.getUpdatedStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isEqualTo(4);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().getFirst().stockCode()).isEqualTo("005930");
        assertThat(result.getFailures().getFirst().reason()).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void authenticationFailureAbortsEntireUpdateWithoutRetry() {
        prepareStocks(LocalDate.of(2026, 8, 13));
        HttpClientErrorException unauthorized =
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED);
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(unauthorized);

        assertThatThrownBy(() -> service.update(BASE_DATE)).isSameAs(unauthorized);

        verify(client).getDailyPrices("005930", BASE_DATE, BASE_DATE);
        verify(client, never()).getDailyPrices("035720", BASE_DATE, BASE_DATE);
    }

    @Test
    void forbiddenFailureAbortsEntireUpdateWithoutRetry() {
        prepareStocks(LocalDate.of(2026, 8, 13));
        HttpClientErrorException forbidden =
                new HttpClientErrorException(HttpStatus.FORBIDDEN);
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(forbidden);

        assertThatThrownBy(() -> service.update(BASE_DATE)).isSameAs(forbidden);

        verify(client).getDailyPrices("005930", BASE_DATE, BASE_DATE);
        verify(client, never()).getDailyPrices("035720", BASE_DATE, BASE_DATE);
    }

    @Test
    void nonRetryableKisAndParsingErrorsAreCalledOnce() {
        prepareStocks(LocalDate.of(2026, 8, 13));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(new KisApiException("BUSINESS_ERROR", "invalid request"));
        when(client.getDailyPrices("035720", BASE_DATE, BASE_DATE))
                .thenThrow(new IllegalArgumentException("invalid response"));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getFailedStockCount()).isEqualTo(2);
        assertThat(result.getApiCallCount()).isEqualTo(2);
        verify(client).getDailyPrices("005930", BASE_DATE, BASE_DATE);
        verify(client).getDailyPrices("035720", BASE_DATE, BASE_DATE);
    }

    @Test
    void writerFailureIsIsolatedAndNextStockContinues() {
        prepareStocks(LocalDate.of(2026, 8, 13));
        List<KisDailyPrice> kospiPrices = List.of(price("2026-08-14"));
        List<KisDailyPrice> kosdaqPrices = List.of(price("2026-08-14"));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE)).thenReturn(kospiPrices);
        when(client.getDailyPrices("035720", BASE_DATE, BASE_DATE)).thenReturn(kosdaqPrices);
        when(writer.write(kospi, kospiPrices)).thenThrow(new IllegalStateException("db failed"));
        when(writer.write(kosdaq, kosdaqPrices)).thenReturn(save(1));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getFailedStockCount()).isEqualTo(1);
        assertThat(result.getUpdatedStockCount()).isEqualTo(1);
        verify(writer, times(2)).write(any(), anyList());
    }

    @Test
    void appliesRequestDelayOnlyBetweenActualApiCalls() throws Exception {
        properties.getDailyPrice().getUpdate().setRequestDelayMs(50);
        prepareStocks(LocalDate.of(2026, 8, 13));
        List<KisDailyPrice> prices = List.of(price("2026-08-14"));
        when(client.getDailyPrices(any(), any(), any())).thenReturn(prices);
        when(writer.write(any(), anyList())).thenReturn(save(1));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        verify(sleeper).sleep(50);
        assertThat(result.getApiCallCount()).isEqualTo(2);
    }

    @Test
    void interruptDuringRequestDelayAbortsEntireUpdateAndRestoresFlag() throws Exception {
        properties.getDailyPrice().getUpdate().setRequestDelayMs(50);
        prepareStocks(LocalDate.of(2026, 8, 13));
        List<KisDailyPrice> prices = List.of(price("2026-08-14"));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE)).thenReturn(prices);
        when(writer.write(kospi, prices)).thenReturn(save(1));
        org.mockito.Mockito.doThrow(new InterruptedException()).when(sleeper).sleep(50);

        try {
            assertThatThrownBy(() -> service.update(BASE_DATE))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(client, never()).getDailyPrices("035720", BASE_DATE, BASE_DATE);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptDuringBackoffAbortsEntireUpdateAndRestoresFlag() throws Exception {
        properties.getDailyPrice().getUpdate().getRetry().setInitialBackoffMs(100);
        prepareStocks(LocalDate.of(2026, 8, 13));
        when(client.getDailyPrices("005930", BASE_DATE, BASE_DATE))
                .thenThrow(new ResourceAccessException("timeout"));
        org.mockito.Mockito.doThrow(new InterruptedException()).when(sleeper).sleep(100);

        try {
            assertThatThrownBy(() -> service.update(BASE_DATE))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(client).getDailyPrices("005930", BASE_DATE, BASE_DATE);
            verify(client, never()).getDailyPrices("035720", BASE_DATE, BASE_DATE);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void catchUpSafeguardFailsStockWithoutApiCall() {
        properties.getDailyPrice().getUpdate().setMaxCatchUpDays(3);
        prepareOneStock(kospi, LocalDate.of(2026, 8, 10));

        DailyPriceUpdateResult result = service.update(BASE_DATE);

        assertThat(result.getFailedStockCount()).isEqualTo(1);
        assertThat(result.getApiCallCount()).isZero();
        assertThat(result.getFailures().getFirst().reason())
                .isEqualTo("MAX_CATCH_UP_DAYS");
        verify(client, never()).getDailyPrices(any(), any(), any());
    }

    @Test
    void rejectsInvalidUpdateConfigurationBeforeSelectingStocks() {
        properties.getDailyPrice().getUpdate().setRequestDelayMs(-1);
        assertInvalidConfiguration();
        properties.getDailyPrice().getUpdate().setRequestDelayMs(0);

        properties.getDailyPrice().getUpdate().setMaxCatchUpDays(0);
        assertInvalidConfiguration();
        properties.getDailyPrice().getUpdate().setMaxCatchUpDays(30);

        properties.getDailyPrice().getUpdate().getRetry().setMaxAttempts(0);
        assertInvalidConfiguration();
        properties.getDailyPrice().getUpdate().getRetry().setMaxAttempts(3);

        properties.getDailyPrice().getUpdate().getRetry().setInitialBackoffMs(-1);
        assertInvalidConfiguration();
        properties.getDailyPrice().getUpdate().getRetry().setInitialBackoffMs(0);

        properties.getDailyPrice().getUpdate().getRetry().setMultiplier(0);
        assertInvalidConfiguration();
    }

    @Test
    void fullUpdateSelectsOnlyKospiAndKosdaqInIdOrder() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of());

        service.update(BASE_DATE);

        ArgumentCaptor<List<MarketType>> markets = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).findByMarketTypeInOrderByIdAsc(markets.capture());
        assertThat(markets.getValue()).containsExactly(MarketType.KOSPI, MarketType.KOSDAQ);
    }

    @Test
    void configuredStockCodeProcessesOnlyMatchingStock() {
        when(stockRepository.findByStockCodeAndMarketTypeIn(any(), anyList()))
                .thenReturn(Optional.of(kospi));
        when(dailyPriceRepository.findLatestTradeDateByStock(kospi))
                .thenReturn(Optional.of(BASE_DATE));

        DailyPriceUpdateResult result = service.updateStock(" 005930 ", BASE_DATE);

        assertThat(result.getTargetStockCount()).isEqualTo(1);
        assertThat(result.getUpToDateStockCount()).isEqualTo(1);
        verify(stockRepository, never()).findByMarketTypeInOrderByIdAsc(anyList());
    }

    @Test
    void unknownOrKonexStockCodeDoesNotFallBackToAllStocks() {
        when(stockRepository.findByStockCodeAndMarketTypeIn(any(), anyList()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStock("123456", BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("123456");

        ArgumentCaptor<List<MarketType>> markets = ArgumentCaptor.forClass(List.class);
        verify(stockRepository).findByStockCodeAndMarketTypeIn(any(), markets.capture());
        assertThat(markets.getValue()).doesNotContain(MarketType.KONEX);
        verify(stockRepository, never()).findByMarketTypeInOrderByIdAsc(anyList());
        verify(client, never()).getDailyPrices(any(), any(), any());
    }

    @Test
    void defaultUpdateUsesCurrentDateInKorea() {
        prepareOneStock(kospi, BASE_DATE);

        DailyPriceUpdateResult result = service.update();

        assertThat(result.getBaseDate()).isEqualTo(BASE_DATE);
    }

    private void prepareOneStock(Stock stock, LocalDate latestDate) {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of(stock));
        when(dailyPriceRepository.findLatestTradeDateByStock(stock))
                .thenReturn(Optional.of(latestDate));
    }

    private void prepareStocks(LocalDate latestDate) {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(anyList()))
                .thenReturn(List.of(kospi, kosdaq));
        when(dailyPriceRepository.findLatestTradeDateByStock(any()))
                .thenReturn(Optional.of(latestDate));
    }

    private Stock stock(long id, String code, MarketType marketType) {
        return Stock.builder().id(id).stockCode(code).stockName(code)
                .marketType(marketType).build();
    }

    private KisDailyPrice price(String tradeDate) {
        return KisDailyPrice.builder().tradeDate(LocalDate.parse(tradeDate))
                .openPrice(1L).highPrice(2L).lowPrice(1L).closePrice(2L)
                .volume(10L).build();
    }

    private StockDailyPriceSaveResult save(int savedCount) {
        return StockDailyPriceSaveResult.builder().requestedCount(savedCount)
                .savedCount(savedCount).skippedCount(0).build();
    }

    private void assertInvalidConfiguration() {
        assertThatThrownBy(() -> service.update(BASE_DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("설정");
        verify(stockRepository, never()).findByMarketTypeInOrderByIdAsc(anyList());
    }
}
