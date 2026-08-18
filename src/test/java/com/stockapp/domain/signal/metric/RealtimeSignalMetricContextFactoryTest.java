package com.stockapp.domain.signal.metric;

import com.stockapp.domain.screening.realtime.RealtimeWatchTarget;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSignalMetricContextFactoryTest {

    private static final LocalDateTime TRADE_DATE_TIME =
            LocalDateTime.of(2026, 8, 18, 10, 15);

    @Test
    void createsContextFromTargetTradeAndCachedDailyHistory() {
        RealtimeDailyHistoryCache cache = mock(RealtimeDailyHistoryCache.class);
        List<DailyPriceData> history = List.of(new DailyPriceData(
                LocalDate.of(2026, 8, 17), 70_000L, 1_000_000L));
        when(cache.get(1L, TRADE_DATE_TIME.toLocalDate(), 20))
                .thenReturn(history);
        RealtimeSignalMetricContextFactory factory =
                new RealtimeSignalMetricContextFactory(cache);

        RealtimeSignalMetricContext context = factory.create(
                target("005930"), trade("005930"), 20);

        assertThat(context.stockId()).isEqualTo(1L);
        assertThat(context.stockCode()).isEqualTo("005930");
        assertThat(context.tradeDateTime()).isEqualTo(TRADE_DATE_TIME);
        assertThat(context.currentPrice()).isEqualTo(71_000L);
        assertThat(context.accumulatedVolume()).isEqualTo(2_500_000L);
        assertThat(context.dailyPrices()).containsExactlyElementsOf(history);
        verify(cache).get(1L, TRADE_DATE_TIME.toLocalDate(), 20);
    }

    @Test
    void rejectsStockCodeMismatchBeforeLoadingHistory() {
        RealtimeDailyHistoryCache cache = mock(RealtimeDailyHistoryCache.class);
        RealtimeSignalMetricContextFactory factory =
                new RealtimeSignalMetricContextFactory(cache);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.create(
                        target("005930"), trade("000660"), 20))
                .withMessage("target and trade stockCode must match");

        verify(cache, never()).get(1L, TRADE_DATE_TIME.toLocalDate(), 20);
    }

    @Test
    void zeroDailyPeriodAvoidsHistoryDatabasePath() {
        RealtimeDailyHistoryCache cache = mock(RealtimeDailyHistoryCache.class);
        when(cache.get(1L, TRADE_DATE_TIME.toLocalDate(), 0))
                .thenReturn(List.of());
        RealtimeSignalMetricContextFactory factory =
                new RealtimeSignalMetricContextFactory(cache);

        RealtimeSignalMetricContext context = factory.create(
                target("005930"), trade("005930"), 0);

        assertThat(context.dailyPrices()).isEmpty();
        verify(cache).get(1L, TRADE_DATE_TIME.toLocalDate(), 0);
    }

    private RealtimeWatchTarget target(String stockCode) {
        return new RealtimeWatchTarget(
                1L, stockCode, List.of(1L));
    }

    private KisRealtimeTradePrice trade(String stockCode) {
        return KisRealtimeTradePrice.builder()
                .stockCode(stockCode)
                .currentPrice(71_000L)
                .accumulatedVolume(2_500_000L)
                .tradeDateTime(TRADE_DATE_TIME)
                .build();
    }
}
