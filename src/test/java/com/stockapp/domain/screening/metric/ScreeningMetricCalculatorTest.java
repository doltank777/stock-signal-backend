package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreeningMetricCalculatorTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    private final ScreeningMetricCalculator calculator =
            new ScreeningMetricCalculator();

    @Test
    void calculatesCurrentPrice() {
        assertCalculated(
                ScreeningMetric.CURRENT_PRICE,
                null,
                context(snapshot(71_000L, 3.5, 1_000L), List.of()),
                "71000");
    }

    @Test
    void currentPriceIsUnavailableWithoutSnapshot() {
        assertThat(calculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, emptyContext()))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"3.5, 3.5", "0.0, 0.0", "-2.75, -2.75"})
    void preservesChangeRateUnit(double changeRate, String expected) {
        assertCalculated(
                ScreeningMetric.CHANGE_RATE,
                null,
                context(snapshot(71_000L, changeRate, 1_000L), List.of()),
                expected);
    }

    @Test
    void changeRateIsUnavailableWithoutSnapshot() {
        assertThat(calculator.calculate(
                ScreeningMetric.CHANGE_RATE, null, emptyContext()))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000L, 0L})
    void calculatesCurrentVolume(long volume) {
        assertCalculated(
                ScreeningMetric.VOLUME,
                null,
                context(snapshot(71_000L, 3.5, volume), List.of()),
                Long.toString(volume));
    }

    @Test
    void volumeIsUnavailableWithoutSnapshot() {
        assertThat(calculator.calculate(
                ScreeningMetric.VOLUME, null, emptyContext()))
                .isEmpty();
    }

    @Test
    void calculatesIntegerAverageVolume() {
        assertCalculated(
                ScreeningMetric.AVERAGE_VOLUME,
                3,
                context(Optional.empty(), dailyVolumes(100, 200, 300)),
                "200");
    }

    @Test
    void calculatesRepeatingAverageVolumeWithDecimal128() {
        BigDecimal expected = BigDecimal.valueOf(302)
                .divide(BigDecimal.valueOf(3), MathContext.DECIMAL128);

        assertCalculated(
                ScreeningMetric.AVERAGE_VOLUME,
                3,
                context(Optional.empty(), dailyVolumes(100, 101, 101)),
                expected);
    }

    @Test
    void averageVolumeUsesOnlyMostRecentPeriod() {
        assertCalculated(
                ScreeningMetric.AVERAGE_VOLUME,
                2,
                context(Optional.empty(), dailyVolumes(10_000, 100, 300)),
                "200");
    }

    @Test
    void averageVolumeIsUnavailableForInsufficientOrEmptyHistory() {
        assertThat(calculator.calculate(
                ScreeningMetric.AVERAGE_VOLUME,
                3,
                context(Optional.empty(), dailyVolumes(100, 200))))
                .isEmpty();
        assertThat(calculator.calculate(
                ScreeningMetric.AVERAGE_VOLUME,
                1,
                context(Optional.empty(), List.of())))
                .isEmpty();
    }

    @Test
    void averageVolumeReturnsZeroWhenAllVolumesAreZero() {
        assertCalculated(
                ScreeningMetric.AVERAGE_VOLUME,
                3,
                context(Optional.empty(), dailyVolumes(0, 0, 0)),
                "0");
    }

    @Test
    void calculatesVolumeRatioAsMultipleWithoutPercentConversion() {
        assertCalculated(
                ScreeningMetric.VOLUME_RATIO,
                2,
                context(snapshot(71_000L, 3.5, 150L), dailyVolumes(100, 100)),
                "1.5");
    }

    @Test
    void calculatesRepeatingVolumeRatioWithDecimal128() {
        BigDecimal expected = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(3), MathContext.DECIMAL128);

        assertCalculated(
                ScreeningMetric.VOLUME_RATIO,
                2,
                context(snapshot(71_000L, 3.5, 1L), dailyVolumes(1, 2)),
                expected);
    }

    @Test
    void volumeRatioUsesOnlyMostRecentPeriodWithoutRoundingAverageFirst() {
        assertCalculated(
                ScreeningMetric.VOLUME_RATIO,
                2,
                context(
                        snapshot(71_000L, 3.5, 1L),
                        dailyVolumes(10_000, 1, 2)),
                BigDecimal.valueOf(2)
                        .divide(BigDecimal.valueOf(3), MathContext.DECIMAL128));
    }

    @Test
    void volumeRatioIsUnavailableWithoutSnapshotOrEnoughHistory() {
        assertThat(calculator.calculate(
                ScreeningMetric.VOLUME_RATIO,
                2,
                context(Optional.empty(), dailyVolumes(100, 100))))
                .isEmpty();
        assertThat(calculator.calculate(
                ScreeningMetric.VOLUME_RATIO,
                2,
                context(snapshot(71_000L, 3.5, 100L), dailyVolumes(100))))
                .isEmpty();
    }

    @Test
    void volumeRatioIsUnavailableWhenSumVolumeIsZero() {
        assertThat(calculator.calculate(
                ScreeningMetric.VOLUME_RATIO,
                2,
                context(snapshot(71_000L, 3.5, 100L), dailyVolumes(0, 0))))
                .isEmpty();
    }

    @Test
    void volumeRatioReturnsZeroWhenCurrentVolumeIsZero() {
        assertCalculated(
                ScreeningMetric.VOLUME_RATIO,
                2,
                context(snapshot(71_000L, 3.5, 0L), dailyVolumes(100, 100)),
                "0");
    }

    @Test
    void calculatesMovingAverage() {
        assertCalculated(
                ScreeningMetric.MOVING_AVERAGE,
                3,
                context(Optional.empty(), dailyClosePrices(10_000, 10_001, 10_002)),
                "10001");
    }

    @Test
    void calculatesDecimalMovingAverage() {
        assertCalculated(
                ScreeningMetric.MOVING_AVERAGE,
                2,
                context(Optional.empty(), dailyClosePrices(10_000, 10_001)),
                "10000.5");
    }

    @Test
    void movingAverageIsUnavailableForInsufficientHistory() {
        assertThat(calculator.calculate(
                ScreeningMetric.MOVING_AVERAGE,
                3,
                context(Optional.empty(), dailyClosePrices(10_000, 10_001))))
                .isEmpty();
    }

    @Test
    void movingAverageUsesRecentPricesAndDoesNotOverflowLong() {
        assertCalculated(
                ScreeningMetric.MOVING_AVERAGE,
                2,
                context(Optional.empty(), dailyClosePrices(
                        1L, Long.MAX_VALUE, Long.MAX_VALUE)),
                BigDecimal.valueOf(Long.MAX_VALUE));
    }

    @Test
    void rejectsNullMetricAndContext() {
        assertThatThrownBy(() -> calculator.calculate(
                null, null, emptyContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metric은 필수입니다.");
        assertThatThrownBy(() -> calculator.calculate(
                ScreeningMetric.CURRENT_PRICE, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context는 필수입니다.");
    }

    @Test
    void rejectsMissingPeriodForPeriodMetric() {
        assertThatThrownBy(() -> calculator.calculate(
                ScreeningMetric.AVERAGE_VOLUME, null, emptyContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AVERAGE_VOLUME에는 period가 필요합니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositivePeriodForPeriodMetric(int period) {
        assertThatThrownBy(() -> calculator.calculate(
                ScreeningMetric.MOVING_AVERAGE, period, emptyContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period는 1 이상이어야 합니다.");
    }

    @Test
    void rejectsPeriodForMetricThatDoesNotUsePeriod() {
        assertThatThrownBy(() -> calculator.calculate(
                ScreeningMetric.CURRENT_PRICE, 1, emptyContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CURRENT_PRICE에는 period를 지정할 수 없습니다.");
    }

    @Test
    void largePositivePeriodIsValidButUnavailableWhenHistoryIsInsufficient() {
        assertThat(calculator.calculate(
                ScreeningMetric.AVERAGE_VOLUME, 10_000, emptyContext()))
                .isEmpty();
    }

    private void assertCalculated(
            ScreeningMetric metric,
            Integer period,
            StockMetricContext context,
            String expected
    ) {
        assertCalculated(metric, period, context, new BigDecimal(expected));
    }

    private void assertCalculated(
            ScreeningMetric metric,
            Integer period,
            StockMetricContext context,
            BigDecimal expected
    ) {
        assertThat(calculator.calculate(metric, period, context))
                .hasValueSatisfying(value ->
                        assertThat(value).isEqualByComparingTo(expected));
    }

    private StockMetricContext emptyContext() {
        return context(Optional.empty(), List.of());
    }

    private StockMetricContext context(
            Optional<LatestStockSnapshot> snapshot,
            List<DailyPriceData> dailyPrices
    ) {
        return new StockMetricContext(
                Stock.builder()
                        .id(1L)
                        .stockCode("005930")
                        .stockName("삼성전자")
                        .marketType(MarketType.KOSPI)
                        .build(),
                BASE_DATE,
                snapshot,
                dailyPrices);
    }

    private Optional<LatestStockSnapshot> snapshot(
            long currentPrice,
            double changeRate,
            long volume
    ) {
        return Optional.of(new LatestStockSnapshot(
                "005930",
                BASE_DATE,
                currentPrice,
                changeRate,
                volume,
                LocalDateTime.of(2026, 8, 14, 12, 0)));
    }

    private List<DailyPriceData> dailyVolumes(long... volumes) {
        List<Long> values = java.util.Arrays.stream(volumes)
                .boxed()
                .toList();
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> new DailyPriceData(
                        BASE_DATE.minusDays(values.size() - index),
                        10_000L + index,
                        values.get(index)))
                .toList();
    }

    private List<DailyPriceData> dailyClosePrices(long... closePrices) {
        List<Long> values = java.util.Arrays.stream(closePrices)
                .boxed()
                .toList();
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> new DailyPriceData(
                        BASE_DATE.minusDays(values.size() - index),
                        values.get(index),
                        100L + index))
                .toList();
    }
}
