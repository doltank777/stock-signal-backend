package com.stockapp.domain.signal.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.metric.StockMetricCalculationSupport;
import com.stockapp.domain.stock.dto.DailyPriceData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RealtimeSignalMetricCalculator {

    private final StockMetricCalculationSupport calculationSupport;

    public Optional<BigDecimal> calculate(
            ScreeningMetric metric,
            Integer period,
            RealtimeSignalMetricContext context
    ) {
        if (metric == null) {
            throw new IllegalArgumentException("metric is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        calculationSupport.validatePeriod(metric, period);

        return switch (metric) {
            case CURRENT_PRICE -> Optional.of(
                    BigDecimal.valueOf(context.currentPrice()));
            case CHANGE_RATE -> Optional.empty();
            case VOLUME -> Optional.of(
                    BigDecimal.valueOf(context.accumulatedVolume()));
            case AVERAGE_VOLUME -> calculationSupport.average(
                    context.recentDailyPrices(period),
                    DailyPriceData::volume,
                    period);
            case VOLUME_RATIO -> calculationSupport.volumeRatio(
                    context.accumulatedVolume(),
                    context.recentDailyPrices(period),
                    period);
            case MOVING_AVERAGE -> calculationSupport.average(
                    context.recentDailyPrices(period),
                    DailyPriceData::closePrice,
                    period);
        };
    }
}
