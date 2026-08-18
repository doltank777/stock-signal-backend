package com.stockapp.domain.signal.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.metric.StockMetricCalculationSupport;
import com.stockapp.domain.stock.dto.DailyPriceData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RealtimeSignalMetricCalculatorTest {

    private final RealtimeSignalMetricCalculator calculator =
            new RealtimeSignalMetricCalculator(
                    new StockMetricCalculationSupport());

    @Test
    void usesRealtimePriceAndAccumulatedVolume() {
        RealtimeSignalMetricContext context = context(
                71_500L, 2_500_000L, history(20));

        assertThat(calculate(ScreeningMetric.CURRENT_PRICE, null, context))
                .isEqualByComparingTo("71500");
        assertThat(calculate(ScreeningMetric.VOLUME, null, context))
                .isEqualByComparingTo("2500000");
        assertThat(calculator.calculate(
                ScreeningMetric.CHANGE_RATE, null, context)).isEmpty();
    }

    @Test
    void calculatesMovingAveragesFromMostRecentHistory() {
        RealtimeSignalMetricContext context = context(
                71_500L, 2_500_000L, history(20));

        assertThat(calculate(
                ScreeningMetric.MOVING_AVERAGE, 5, context))
                .isEqualByComparingTo("1800");
        assertThat(calculate(
                ScreeningMetric.MOVING_AVERAGE, 10, context))
                .isEqualByComparingTo("1550");
    }

    @Test
    void calculatesAverageVolumeAndVolumeRatioWithDecimal128() {
        List<DailyPriceData> history = List.of(
                daily(1, 100L, 1L),
                daily(2, 200L, 2L),
                daily(3, 300L, 4L));
        RealtimeSignalMetricContext context = context(500L, 10L, history);

        assertThat(calculate(
                ScreeningMetric.AVERAGE_VOLUME, 3, context))
                .isEqualByComparingTo(BigDecimal.valueOf(7)
                        .divide(BigDecimal.valueOf(3), MathContext.DECIMAL128));
        assertThat(calculate(
                ScreeningMetric.VOLUME_RATIO, 3, context))
                .isEqualByComparingTo(BigDecimal.valueOf(30)
                        .divide(BigDecimal.valueOf(7), MathContext.DECIMAL128));
    }

    @Test
    void returnsEmptyForInsufficientOrZeroVolumeHistory() {
        RealtimeSignalMetricContext insufficient = context(
                500L, 10L, history(9));
        RealtimeSignalMetricContext zeroVolume = context(
                500L, 10L, List.of(
                        daily(1, 100L, 0L), daily(2, 200L, 0L)));

        assertThat(calculator.calculate(
                ScreeningMetric.MOVING_AVERAGE, 10, insufficient)).isEmpty();
        assertThat(calculator.calculate(
                ScreeningMetric.VOLUME_RATIO, 2, zeroVolume)).isEmpty();
    }

    @Test
    void rejectsInvalidPeriods() {
        RealtimeSignalMetricContext context = context(
                500L, 10L, history(1));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        ScreeningMetric.MOVING_AVERAGE, 0, context));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        ScreeningMetric.CURRENT_PRICE, 1, context));
    }

    private BigDecimal calculate(
            ScreeningMetric metric,
            Integer period,
            RealtimeSignalMetricContext context
    ) {
        return calculator.calculate(metric, period, context).orElseThrow();
    }

    private RealtimeSignalMetricContext context(
            long currentPrice,
            long volume,
            List<DailyPriceData> history
    ) {
        return new RealtimeSignalMetricContext(
                1L,
                "005930",
                LocalDateTime.of(2026, 8, 30, 10, 15),
                currentPrice,
                volume,
                history);
    }

    private List<DailyPriceData> history(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(day -> daily(
                        day, day * 100L, day * 1_000L))
                .toList();
    }

    private DailyPriceData daily(int day, Long closePrice, Long volume) {
        return new DailyPriceData(
                LocalDate.of(2026, 8, day), closePrice, volume);
    }
}
