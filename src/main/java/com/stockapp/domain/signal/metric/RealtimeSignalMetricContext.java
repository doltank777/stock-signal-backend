package com.stockapp.domain.signal.metric;

import com.stockapp.domain.stock.dto.DailyPriceData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RealtimeSignalMetricContext(
        Long stockId,
        String stockCode,
        LocalDateTime tradeDateTime,
        long currentPrice,
        long accumulatedVolume,
        List<DailyPriceData> dailyPrices
) {

    public RealtimeSignalMetricContext {
        Objects.requireNonNull(stockId, "stockId is required");
        Objects.requireNonNull(stockCode, "stockCode is required");
        if (stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
        Objects.requireNonNull(tradeDateTime, "tradeDateTime is required");
        dailyPrices = List.copyOf(
                Objects.requireNonNull(dailyPrices, "dailyPrices are required"));
        validateDailyPrices(tradeDateTime.toLocalDate(), dailyPrices);
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

    private static void validateDailyPrices(
            LocalDate tradeDate,
            List<DailyPriceData> dailyPrices
    ) {
        LocalDate previousDate = null;
        for (DailyPriceData dailyPrice : dailyPrices) {
            Objects.requireNonNull(dailyPrice, "dailyPrice is required");
            LocalDate dailyTradeDate = Objects.requireNonNull(
                    dailyPrice.tradeDate(), "dailyPrice tradeDate is required");
            if (!dailyTradeDate.isBefore(tradeDate)) {
                throw new IllegalArgumentException(
                        "dailyPrice tradeDate must be before realtime tradeDate");
            }
            if (previousDate != null && !dailyTradeDate.isAfter(previousDate)) {
                throw new IllegalArgumentException(
                        "dailyPrices must be ordered by tradeDate ascending");
            }
            previousDate = dailyTradeDate;
        }
    }
}
