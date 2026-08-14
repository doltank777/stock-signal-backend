package com.stockapp.domain.screening.metric;

import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMetricContextTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Test
    void storesSnapshotAndDailyPrices() {
        LatestStockSnapshot snapshot = snapshot();
        List<DailyPriceData> dailyPrices = dailyPrices(1, 2, 3);

        StockMetricContext context = new StockMetricContext(
                stock(), BASE_DATE, Optional.of(snapshot), dailyPrices);

        assertThat(context.stock().getStockCode()).isEqualTo("005930");
        assertThat(context.baseDate()).isEqualTo(BASE_DATE);
        assertThat(context.snapshot()).contains(snapshot);
        assertThat(context.dailyPrices()).containsExactlyElementsOf(dailyPrices);
    }

    @Test
    void defensivelyCopiesDailyPrices() {
        List<DailyPriceData> source = new ArrayList<>(dailyPrices(1, 2, 3));
        StockMetricContext context = context(Optional.of(snapshot()), source);

        source.clear();

        assertThat(context.dailyPrices()).hasSize(3);
        assertThatThrownBy(() -> context.dailyPrices().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsMostRecentDailyPricesInAscendingOrder() {
        StockMetricContext context = context(
                Optional.of(snapshot()), dailyPrices(1, 2, 3, 4, 5));

        List<DailyPriceData> recentPrices = context.recentDailyPrices(3)
                .orElseThrow();

        assertThat(recentPrices)
                .extracting(DailyPriceData::tradeDate)
                .containsExactly(
                        BASE_DATE.minusDays(3),
                        BASE_DATE.minusDays(2),
                        BASE_DATE.minusDays(1));
    }

    @Test
    void returnsEmptyWhenDailyHistoryIsInsufficient() {
        StockMetricContext context = context(
                Optional.of(snapshot()), dailyPrices(1, 2));

        assertThat(context.recentDailyPrices(3)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidRecentPeriod(int period) {
        StockMetricContext context = context(
                Optional.of(snapshot()), dailyPrices(1));

        assertThatThrownBy(() -> context.recentDailyPrices(period))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period는 1 이상이어야 합니다.");
    }

    private StockMetricContext context(
            Optional<LatestStockSnapshot> snapshot,
            List<DailyPriceData> dailyPrices
    ) {
        return new StockMetricContext(stock(), BASE_DATE, snapshot, dailyPrices);
    }

    private Stock stock() {
        return Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build();
    }

    private LatestStockSnapshot snapshot() {
        return new LatestStockSnapshot(
                "005930", BASE_DATE, 71_000L, 3.5,
                1_000L, LocalDateTime.of(2026, 8, 14, 12, 0));
    }

    private List<DailyPriceData> dailyPrices(int... daysFromOldest) {
        return java.util.Arrays.stream(daysFromOldest)
                .mapToObj(day -> new DailyPriceData(
                        BASE_DATE.minusDays(6 - day),
                        10_000L + day,
                        100L + day))
                .toList();
    }
}
