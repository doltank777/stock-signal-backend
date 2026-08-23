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
        Optional<OperationalCurrentMetrics> operationalCurrent,
        List<DailyPriceData> dailyPrices
) {

    public StockMetricContext(
            Stock stock,
            LocalDate baseDate,
            Optional<LatestStockSnapshot> snapshot,
            List<DailyPriceData> dailyPrices
    ) {
        this(stock, baseDate, snapshot, Optional.empty(), dailyPrices);
    }

    public StockMetricContext {
        Objects.requireNonNull(stock, "stock은 필수입니다.");
        Objects.requireNonNull(baseDate, "baseDate는 필수입니다.");
        Objects.requireNonNull(snapshot, "snapshot Optional은 필수입니다.");
        Objects.requireNonNull(
                operationalCurrent,
                "operationalCurrent Optional is required");
        if (snapshot.isPresent() && operationalCurrent.isPresent()) {
            throw new IllegalArgumentException(
                    "snapshot and operationalCurrent are mutually exclusive");
        }
        dailyPrices = List.copyOf(
                Objects.requireNonNull(dailyPrices, "dailyPrices는 필수입니다."));
        validateSnapshot(stock, baseDate, snapshot);
        validateDailyPrices(baseDate, dailyPrices);
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

    private static void validateSnapshot(
            Stock stock,
            LocalDate baseDate,
            Optional<LatestStockSnapshot> snapshot
    ) {
        snapshot.ifPresent(value -> {
            if (!Objects.equals(stock.getStockCode(), value.stockCode())) {
                throw new IllegalArgumentException(
                        "snapshot 종목코드가 context 종목과 일치하지 않습니다.");
            }
            if (!Objects.equals(baseDate, value.tradeDate())) {
                throw new IllegalArgumentException(
                        "snapshot 거래일이 context 기준일과 일치하지 않습니다.");
            }
        });
    }

    private static void validateDailyPrices(
            LocalDate baseDate,
            List<DailyPriceData> dailyPrices
    ) {
        LocalDate previousDate = null;
        for (DailyPriceData dailyPrice : dailyPrices) {
            LocalDate tradeDate = Objects.requireNonNull(
                    dailyPrice.tradeDate(), "dailyPrice 거래일은 필수입니다.");
            if (!tradeDate.isBefore(baseDate)) {
                throw new IllegalArgumentException(
                        "dailyPrice 거래일은 context 기준일보다 이전이어야 합니다.");
            }
            if (previousDate != null && !tradeDate.isAfter(previousDate)) {
                throw new IllegalArgumentException(
                        "dailyPrices는 거래일 오름차순이어야 합니다.");
            }
            previousDate = tradeDate;
        }
    }
}
