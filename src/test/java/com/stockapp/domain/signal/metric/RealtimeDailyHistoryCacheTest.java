package com.stockapp.domain.signal.metric;

import com.stockapp.domain.stock.dto.DailyPriceData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeDailyHistoryCacheTest {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 8, 18);

    @Test
    void reusesSameDateHistoryWhenLoadedPeriodIsSufficient() {
        RealtimeDailyHistoryLoader loader = mock(
                RealtimeDailyHistoryLoader.class);
        List<DailyPriceData> history = history(20);
        when(loader.load(1L, TRADE_DATE, 20)).thenReturn(history);
        RealtimeDailyHistoryCache cache = new RealtimeDailyHistoryCache(loader);

        assertThat(cache.get(1L, TRADE_DATE, 20)).isEqualTo(history);
        assertThat(cache.get(1L, TRADE_DATE, 5)).isEqualTo(history);

        verify(loader).load(1L, TRADE_DATE, 20);
    }

    @Test
    void reloadsForLargerPeriodOrNewTradeDate() {
        RealtimeDailyHistoryLoader loader = mock(
                RealtimeDailyHistoryLoader.class);
        LocalDate nextDate = TRADE_DATE.plusDays(1);
        when(loader.load(1L, TRADE_DATE, 5)).thenReturn(history(5));
        when(loader.load(1L, TRADE_DATE, 20)).thenReturn(history(20));
        when(loader.load(1L, nextDate, 20)).thenReturn(history(20));
        RealtimeDailyHistoryCache cache = new RealtimeDailyHistoryCache(loader);

        cache.get(1L, TRADE_DATE, 5);
        cache.get(1L, TRADE_DATE, 20);
        cache.get(1L, nextDate, 20);

        verify(loader).load(1L, TRADE_DATE, 5);
        verify(loader).load(1L, TRADE_DATE, 20);
        verify(loader).load(1L, nextDate, 20);
    }

    @Test
    void zeroPeriodDoesNotLoadHistory() {
        RealtimeDailyHistoryLoader loader = mock(
                RealtimeDailyHistoryLoader.class);
        RealtimeDailyHistoryCache cache = new RealtimeDailyHistoryCache(loader);

        assertThat(cache.get(1L, TRADE_DATE, 0)).isEmpty();

        verify(loader, never()).load(1L, TRADE_DATE, 0);
    }

    private List<DailyPriceData> history(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new DailyPriceData(
                        TRADE_DATE.minusDays(count - index),
                        100L + index,
                        1_000L + index))
                .toList();
    }
}
