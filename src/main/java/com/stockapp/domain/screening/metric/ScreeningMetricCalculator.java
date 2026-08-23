package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.stock.dto.DailyPriceData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ScreeningMetricCalculator {

    private final StockMetricCalculationSupport calculationSupport;

    public Optional<BigDecimal> calculate(
            ScreeningMetric metric,
            Integer period,
            StockMetricContext context
    ) {
        if (metric == null) {
            throw new IllegalArgumentException("metric은 필수입니다.");
        }
        if (context == null) {
            throw new IllegalArgumentException("context는 필수입니다.");
        }
        calculationSupport.validatePeriod(metric, period);

        return switch (metric) {
            case CURRENT_PRICE -> context.operationalCurrent()
                    .map(OperationalCurrentMetrics::currentPrice)
                    .or(() -> context.snapshot().map(snapshot ->
                            BigDecimal.valueOf(snapshot.currentPrice())));
            case CHANGE_RATE -> context.operationalCurrent()
                    .flatMap(OperationalCurrentMetrics::changeRate)
                    .or(() -> context.snapshot().map(snapshot ->
                            BigDecimal.valueOf(snapshot.changeRate())));
            case VOLUME -> context.operationalCurrent()
                    .map(OperationalCurrentMetrics::volume)
                    .or(() -> context.snapshot().map(snapshot ->
                            BigDecimal.valueOf(snapshot.volume())));
            case AVERAGE_VOLUME -> calculationSupport.average(
                    context.recentDailyPrices(period),
                    DailyPriceData::volume,
                    period);
            case VOLUME_RATIO -> currentVolume(context)
                    .flatMap(volume -> calculationSupport.volumeRatio(
                            volume.longValueExact(),
                            context.recentDailyPrices(period),
                            period));
            case MOVING_AVERAGE -> calculationSupport.average(
                    context.recentDailyPrices(period),
                    DailyPriceData::closePrice,
                    period);
        };
    }

    private Optional<BigDecimal> currentVolume(StockMetricContext context) {
        return context.operationalCurrent()
                .map(OperationalCurrentMetrics::volume)
                .or(() -> context.snapshot().map(snapshot ->
                        BigDecimal.valueOf(snapshot.volume())));
    }
}
