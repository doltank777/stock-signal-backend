package com.stockapp.domain.signal.metric;

import com.stockapp.domain.stock.dto.DailyPriceData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class RealtimeDailyHistoryCache {

    private final RealtimeDailyHistoryLoader historyLoader;
    private final ConcurrentMap<Long, CacheEntry> entries =
            new ConcurrentHashMap<>();

    public List<DailyPriceData> get(
            Long stockId,
            LocalDate tradeDate,
            int requiredPeriod
    ) {
        Objects.requireNonNull(stockId, "stockId is required");
        Objects.requireNonNull(tradeDate, "tradeDate is required");
        if (requiredPeriod < 0) {
            throw new IllegalArgumentException(
                    "requiredPeriod must not be negative");
        }
        if (requiredPeriod == 0) {
            return List.of();
        }

        CacheEntry entry = entries.compute(stockId, (ignored, existing) -> {
            if (existing != null
                    && existing.tradeDate().equals(tradeDate)
                    && existing.loadedPeriod() >= requiredPeriod) {
                return existing;
            }
            List<DailyPriceData> loaded = historyLoader.load(
                    stockId, tradeDate, requiredPeriod);
            return new CacheEntry(
                    tradeDate, requiredPeriod, List.copyOf(loaded));
        });
        return entry.dailyPrices();
    }

    private record CacheEntry(
            LocalDate tradeDate,
            int loadedPeriod,
            List<DailyPriceData> dailyPrices
    ) {
    }
}
