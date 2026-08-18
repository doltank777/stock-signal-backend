package com.stockapp.domain.signal.metric;

import com.stockapp.domain.stock.dto.DailyPriceData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeSignalMetricContextTest {

    private static final LocalDateTime TRADE_DATE_TIME =
            LocalDateTime.of(2026, 8, 18, 10, 15);

    @Test
    void defensivelyCopiesHistoryAndReturnsMostRecentPeriod() {
        List<DailyPriceData> source = new ArrayList<>(List.of(
                daily(1, 100L, 1_000L),
                daily(2, 200L, 2_000L),
                daily(3, 300L, 3_000L)));
        RealtimeSignalMetricContext context = context(source);

        source.clear();

        assertThat(context.dailyPrices()).hasSize(3);
        assertThat(context.recentDailyPrices(2).orElseThrow())
                .extracting(DailyPriceData::closePrice)
                .containsExactly(200L, 300L);
        assertThatThrownBy(() -> context.dailyPrices().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsEmptyForInsufficientHistory() {
        assertThat(context(List.of(daily(1, 100L, 1_000L)))
                .recentDailyPrices(2)).isEmpty();
    }

    @Test
    void rejectsCurrentDateAndUnsortedHistory() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> context(List.of(new DailyPriceData(
                        TRADE_DATE_TIME.toLocalDate(), 100L, 1_000L))))
                .withMessage(
                        "dailyPrice tradeDate must be before realtime tradeDate");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> context(List.of(
                        daily(2, 200L, 2_000L),
                        daily(1, 100L, 1_000L))))
                .withMessage(
                        "dailyPrices must be ordered by tradeDate ascending");
    }

    private RealtimeSignalMetricContext context(List<DailyPriceData> history) {
        return new RealtimeSignalMetricContext(
                1L, "005930", TRADE_DATE_TIME,
                70_000L, 1_000_000L, history);
    }

    private DailyPriceData daily(int day, Long closePrice, Long volume) {
        return new DailyPriceData(
                LocalDate.of(2026, 8, day), closePrice, volume);
    }
}
