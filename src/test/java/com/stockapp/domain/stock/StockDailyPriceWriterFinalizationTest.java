package com.stockapp.domain.stock;

import com.stockapp.external.kis.dto.KisDailyPrice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDailyPriceWriterFinalizationTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 20);

    @Mock StockDailyPriceRepository repository;

    @Test
    void insertsMissingTargetDate() {
        Stock stock = stock();
        KisDailyPrice target = price(71_000L, 12_000_000L);
        when(repository.findByStockAndTradeDate(stock, TARGET_DATE))
                .thenReturn(Optional.empty());
        StockDailyPriceWriter writer = new StockDailyPriceWriter(repository);

        DailyPriceFinalizationStatus status = writer.finalizePrice(stock, target);

        ArgumentCaptor<StockDailyPrice> captor =
                ArgumentCaptor.forClass(StockDailyPrice.class);
        verify(repository).save(captor.capture());
        assertThat(status).isEqualTo(DailyPriceFinalizationStatus.INSERTED);
        assertValues(captor.getValue(), 71_000L, 12_000_000L);
    }

    @Test
    void updatesDifferentFinalizedValuesAndKeepsCollectedAt() {
        Stock stock = stock();
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 20, 14, 0);
        StockDailyPrice existing = entity(stock, 70_000L, 10_000_000L, collectedAt);
        when(repository.findByStockAndTradeDate(stock, TARGET_DATE))
                .thenReturn(Optional.of(existing));
        StockDailyPriceWriter writer = new StockDailyPriceWriter(repository);

        DailyPriceFinalizationStatus status = writer.finalizePrice(
                stock, price(71_000L, 12_000_000L));

        assertThat(status).isEqualTo(DailyPriceFinalizationStatus.UPDATED);
        assertValues(existing, 71_000L, 12_000_000L);
        assertThat(existing.getCollectedAt()).isEqualTo(collectedAt);
        verify(repository, never()).save(existing);
    }

    @Test
    void unchangedValuesDoNotSaveOrMutate() {
        Stock stock = stock();
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 20, 16, 20);
        StockDailyPrice existing = entity(stock, 71_000L, 12_000_000L, collectedAt);
        when(repository.findByStockAndTradeDate(stock, TARGET_DATE))
                .thenReturn(Optional.of(existing));
        StockDailyPriceWriter writer = new StockDailyPriceWriter(repository);

        DailyPriceFinalizationStatus status = writer.finalizePrice(
                stock, price(71_000L, 12_000_000L));

        assertThat(status).isEqualTo(DailyPriceFinalizationStatus.UNCHANGED);
        assertThat(existing.getCollectedAt()).isEqualTo(collectedAt);
        verify(repository, never()).save(existing);
    }

    private void assertValues(
            StockDailyPrice actual,
            long close,
            long volume
    ) {
        assertThat(actual.getTradeDate()).isEqualTo(TARGET_DATE);
        assertThat(actual.getOpenPrice()).isEqualTo(69_000L);
        assertThat(actual.getHighPrice()).isEqualTo(72_000L);
        assertThat(actual.getLowPrice()).isEqualTo(68_000L);
        assertThat(actual.getClosePrice()).isEqualTo(close);
        assertThat(actual.getVolume()).isEqualTo(volume);
    }

    private Stock stock() {
        return Stock.builder().id(1L).stockCode("005930")
                .stockName("삼성전자").marketType(MarketType.KOSPI).build();
    }

    private KisDailyPrice price(long close, long volume) {
        return KisDailyPrice.builder().tradeDate(TARGET_DATE)
                .openPrice(69_000L).highPrice(72_000L).lowPrice(68_000L)
                .closePrice(close).volume(volume).build();
    }

    private StockDailyPrice entity(
            Stock stock,
            long close,
            long volume,
            LocalDateTime collectedAt
    ) {
        return StockDailyPrice.builder().id(10L).stock(stock)
                .tradeDate(TARGET_DATE).openPrice(69_000L)
                .highPrice(72_000L).lowPrice(68_000L)
                .closePrice(close).volume(volume).collectedAt(collectedAt)
                .build();
    }
}
