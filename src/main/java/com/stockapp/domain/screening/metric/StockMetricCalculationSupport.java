package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.stock.dto.DailyPriceData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Component
public class StockMetricCalculationSupport {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    public Optional<BigDecimal> average(
            Optional<List<DailyPriceData>> recentPrices,
            Function<DailyPriceData, Long> valueExtractor,
            int period
    ) {
        return recentPrices.map(prices -> sum(prices, valueExtractor)
                .divide(BigDecimal.valueOf(period), CALCULATION_CONTEXT));
    }

    public Optional<BigDecimal> volumeRatio(
            long currentVolume,
            Optional<List<DailyPriceData>> recentPrices,
            int period
    ) {
        if (recentPrices.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal sumVolume = sum(
                recentPrices.get(), DailyPriceData::volume);
        if (sumVolume.signum() == 0) {
            return Optional.empty();
        }

        BigDecimal numerator = BigDecimal.valueOf(currentVolume)
                .multiply(BigDecimal.valueOf(period));
        return Optional.of(numerator.divide(
                sumVolume, CALCULATION_CONTEXT));
    }

    public void validatePeriod(ScreeningMetric metric, Integer period) {
        boolean periodRequired = metric == ScreeningMetric.AVERAGE_VOLUME
                || metric == ScreeningMetric.VOLUME_RATIO
                || metric == ScreeningMetric.MOVING_AVERAGE;

        if (periodRequired && period == null) {
            throw new IllegalArgumentException(
                    metric + "에는 period가 필요합니다.");
        }
        if (periodRequired && period < 1) {
            throw new IllegalArgumentException("period는 1 이상이어야 합니다.");
        }
        if (!periodRequired && period != null) {
            throw new IllegalArgumentException(
                    metric + "에는 period를 지정할 수 없습니다.");
        }
    }

    private BigDecimal sum(
            List<DailyPriceData> prices,
            Function<DailyPriceData, Long> valueExtractor
    ) {
        return prices.stream()
                .map(valueExtractor)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
