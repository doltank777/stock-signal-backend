package com.stockapp.domain.screening.metric;

import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StockMetricContext(
        Stock stock,
        LocalDate baseDate,
        Optional<LatestStockSnapshot> snapshot,
        List<DailyPriceData> dailyPrices
) {

    public StockMetricContext {
        Objects.requireNonNull(stock, "stock은 필수입니다.");
        Objects.requireNonNull(baseDate, "baseDate는 필수입니다.");
        Objects.requireNonNull(snapshot, "snapshot Optional은 필수입니다.");
        dailyPrices = List.copyOf(
                Objects.requireNonNull(dailyPrices, "dailyPrices는 필수입니다."));
    }

    public Optional<List<DailyPriceData>> recentDailyPrices(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period는 1 이상이어야 합니다.");
        }
        if (dailyPrices.size() < period) {
            return Optional.empty();
        }

        return Optional.of(List.copyOf(dailyPrices.subList(
                dailyPrices.size() - period,
                dailyPrices.size())));
    }
}
