package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationResult;
import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPriceFinalizationServiceTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 20);

    @Mock StockRepository stockRepository;
    @Mock KisDailyPriceClient client;
    @Mock StockDailyPriceWriter writer;

    private DailyPriceFinalizationService service;
    private Stock stock;

    @BeforeEach
    void setUp() {
        service = new DailyPriceFinalizationService(
                stockRepository, client, writer);
        stock = Stock.builder().id(1L).stockCode("005930")
                .stockName("삼성전자").marketType(MarketType.KOSPI).build();
    }

    @Test
    void finalizesOnlyExactTargetDate() {
        KisDailyPrice otherDate = price(TARGET_DATE.minusDays(1));
        KisDailyPrice target = price(TARGET_DATE);
        prepareStock();
        when(client.getDailyPrices("005930", TARGET_DATE, TARGET_DATE))
                .thenReturn(List.of(otherDate, target));
        when(writer.finalizePrice(stock, target))
                .thenReturn(DailyPriceFinalizationStatus.INSERTED);

        DailyPriceFinalizationResult result = service.finalizeStock(
                " 005930 ", TARGET_DATE);

        assertThat(result.status()).isEqualTo(
                DailyPriceFinalizationStatus.INSERTED);
        verify(writer).finalizePrice(stock, target);
        verify(writer, never()).finalizePrice(stock, otherDate);
    }

    @Test
    void reportsNoDataWithoutWritingAnotherDate() {
        prepareStock();
        when(client.getDailyPrices("005930", TARGET_DATE, TARGET_DATE))
                .thenReturn(List.of(price(TARGET_DATE.minusDays(1))));

        DailyPriceFinalizationResult result = service.finalizeStock(
                "005930", TARGET_DATE);

        assertThat(result.status()).isEqualTo(
                DailyPriceFinalizationStatus.NO_DATA);
        verify(writer, never()).finalizePrice(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void alwaysCallsKisBeforeWriterCanUpdateExistingRow() {
        KisDailyPrice target = price(TARGET_DATE);
        prepareStock();
        when(client.getDailyPrices("005930", TARGET_DATE, TARGET_DATE))
                .thenReturn(List.of(target));
        when(writer.finalizePrice(stock, target))
                .thenReturn(DailyPriceFinalizationStatus.UPDATED);

        DailyPriceFinalizationResult result = service.finalizeStock(
                "005930", TARGET_DATE);

        verify(client).getDailyPrices("005930", TARGET_DATE, TARGET_DATE);
        assertThat(result.status()).isEqualTo(
                DailyPriceFinalizationStatus.UPDATED);
    }

    @Test
    void propagatesKisFailureWithoutWriting() {
        prepareStock();
        KisApiException failure = new KisApiException("TEST001", "failure");
        when(client.getDailyPrices("005930", TARGET_DATE, TARGET_DATE))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.finalizeStock("005930", TARGET_DATE))
                .isSameAs(failure);
        verify(writer, never()).finalizePrice(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsKonexOrUnknownStockBeforeKisCall() {
        when(stockRepository.findByStockCodeAndMarketTypeIn(
                "123456", List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalizeStock("123456", TARGET_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("123456");
        verify(client, never()).getDailyPrices(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private void prepareStock() {
        when(stockRepository.findByStockCodeAndMarketTypeIn(
                "005930", List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(Optional.of(stock));
    }

    private KisDailyPrice price(LocalDate tradeDate) {
        return KisDailyPrice.builder().tradeDate(tradeDate)
                .openPrice(69_000L).highPrice(72_000L).lowPrice(68_000L)
                .closePrice(71_000L).volume(12_000_000L).build();
    }
}
