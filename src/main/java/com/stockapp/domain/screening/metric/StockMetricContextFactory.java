package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMarketDataQueryService;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StockMetricContextFactory {

    private final StockMarketDataQueryService stockMarketDataQueryService;

    public StockMetricContext create(
            Stock stock,
            SearchCondition condition,
            LocalDate baseDate
    ) {
        validateInputs(stock, condition, baseDate);

        DataRequirements requirements = analyze(condition);

        Optional<LatestStockSnapshot> snapshot = requirements.snapshotRequired
                ? stockMarketDataQueryService.findLatestSnapshotForDate(stock, baseDate)
                : Optional.empty();
        List<DailyPriceData> dailyPrices = requirements.maxDailyPeriod > 0
                ? stockMarketDataQueryService.findRecentDailyPricesBefore(
                        stock, baseDate, requirements.maxDailyPeriod)
                : List.of();

        return new StockMetricContext(stock, baseDate, snapshot, dailyPrices);
    }

    private DataRequirements analyze(SearchCondition condition) {
        DataRequirements requirements = new DataRequirements();
        int screeningRuleCount = 0;

        for (SearchConditionRule rule : condition.getRules()) {
            if (rule.getStage() != ScreeningStage.SCREENING) {
                continue;
            }

            screeningRuleCount++;
            includeMetric(
                    rule.getLeftMetric(), rule.getLeftPeriod(), requirements);

            if (rule.getRightType() == null) {
                throw new IllegalArgumentException("rightType is required");
            }
            if (rule.getRightType() == ScreeningRightType.METRIC) {
                includeMetric(
                        rule.getRightMetric(), rule.getRightPeriod(), requirements);
            }
        }

        if (screeningRuleCount == 0) {
            throw new IllegalArgumentException(
                    "at least one SCREENING rule is required");
        }
        return requirements;
    }

    private void includeMetric(
            ScreeningMetric metric,
            Integer period,
            DataRequirements requirements
    ) {
        if (metric == null) {
            throw new IllegalArgumentException("metric is required");
        }

        if (requiresSnapshot(metric)) {
            requirements.snapshotRequired = true;
        }

        if (requiresDaily(metric)) {
            if (period == null || period < 1) {
                throw new IllegalArgumentException(
                        metric + " requires a positive period");
            }
            requirements.maxDailyPeriod = Math.max(
                    requirements.maxDailyPeriod, period);
        }
    }

    private boolean requiresSnapshot(ScreeningMetric metric) {
        return switch (metric) {
            case CURRENT_PRICE, CHANGE_RATE, VOLUME, VOLUME_RATIO -> true;
            case AVERAGE_VOLUME, MOVING_AVERAGE -> false;
        };
    }

    private boolean requiresDaily(ScreeningMetric metric) {
        return switch (metric) {
            case AVERAGE_VOLUME, VOLUME_RATIO, MOVING_AVERAGE -> true;
            case CURRENT_PRICE, CHANGE_RATE, VOLUME -> false;
        };
    }

    private void validateInputs(
            Stock stock,
            SearchCondition condition,
            LocalDate baseDate
    ) {
        if (stock == null) {
            throw new IllegalArgumentException("stock is required");
        }
        if (condition == null) {
            throw new IllegalArgumentException("condition is required");
        }
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }
    }

    private static class DataRequirements {

        private boolean snapshotRequired;
        private int maxDailyPeriod;
    }
}
