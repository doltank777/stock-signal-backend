package com.stockapp.domain.signal.metric;

import com.stockapp.domain.screening.realtime.RealtimeWatchTarget;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RealtimeSignalMetricContextFactory {

    private final RealtimeDailyHistoryCache dailyHistoryCache;

    public RealtimeSignalMetricContext create(
            RealtimeWatchTarget target,
            KisRealtimeTradePrice trade,
            int requiredDailyPeriod
    ) {
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(trade, "trade is required");
        if (requiredDailyPeriod < 0) {
            throw new IllegalArgumentException(
                    "requiredDailyPeriod must not be negative");
        }
        if (!target.stockCode().equals(trade.getStockCode())) {
            throw new IllegalArgumentException(
                    "target and trade stockCode must match");
        }

        LocalDateTime tradeDateTime = Objects.requireNonNull(
                trade.getTradeDateTime(), "tradeDateTime is required");
        List<DailyPriceData> dailyPrices = dailyHistoryCache.get(
                target.stockId(),
                tradeDateTime.toLocalDate(),
                requiredDailyPeriod);

        return new RealtimeSignalMetricContext(
                target.stockId(),
                target.stockCode(),
                tradeDateTime,
                trade.getCurrentPrice(),
                trade.getAccumulatedVolume(),
                dailyPrices);
    }
}
