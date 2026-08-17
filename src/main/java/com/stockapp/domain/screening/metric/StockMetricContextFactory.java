package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.ScreeningStockDataException;
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
    private final ScreeningDataRequirementAnalyzer requirementAnalyzer;

    public StockMetricContext create(
            Stock stock,
            SearchCondition condition,
            LocalDate baseDate
    ) {
        if (condition == null) {
            throw new IllegalArgumentException("condition is required");
        }
        return createWithRequirements(
                stock, requirementAnalyzer.analyze(condition), baseDate);
    }

    public StockMetricContext createWithRequirements(
            Stock stock,
            ScreeningDataRequirements requirements,
            LocalDate baseDate
    ) {
        validateInputs(stock, requirements, baseDate);

        Optional<LatestStockSnapshot> snapshot = requirements.snapshotRequired()
                ? stockMarketDataQueryService.findLatestSnapshotForDate(stock, baseDate)
                : Optional.empty();
        List<DailyPriceData> dailyPrices = requirements.maxDailyPeriod() > 0
                ? stockMarketDataQueryService.findRecentDailyPricesBefore(
                        stock, baseDate, requirements.maxDailyPeriod())
                : List.of();

        try {
            return new StockMetricContext(
                    stock, baseDate, snapshot, dailyPrices);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ScreeningStockDataException(
                    "invalid screening market data for stock: "
                            + stock.getStockCode(),
                    exception);
        }
    }

    private void validateInputs(
            Stock stock,
            ScreeningDataRequirements requirements,
            LocalDate baseDate
    ) {
        if (stock == null) {
            throw new IllegalArgumentException("stock is required");
        }
        if (requirements == null) {
            throw new IllegalArgumentException("requirements are required");
        }
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }
    }
}
