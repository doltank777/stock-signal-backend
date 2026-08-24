package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.stock.MarketType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DesiredRealtimeTarget(
        Long stockId,
        String stockCode,
        String stockName,
        MarketType market,
        int effectivePriority,
        int effectiveScreeningScore,
        List<DesiredRealtimeCondition> matchedConditions
) {

    public DesiredRealtimeTarget {
        Objects.requireNonNull(stockId, "stockId is required");
        requireText(stockCode, "stockCode");
        requireText(stockName, "stockName");
        Objects.requireNonNull(market, "market is required");
        matchedConditions = List.copyOf(Objects.requireNonNull(
                matchedConditions, "matchedConditions are required"));
        if (matchedConditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one matched condition is required");
        }
        Set<Long> conditionIds = new HashSet<>();
        for (DesiredRealtimeCondition condition : matchedConditions) {
            Objects.requireNonNull(condition, "matched condition is required");
            if (!conditionIds.add(condition.searchConditionId())) {
                throw new IllegalArgumentException(
                        "duplicate searchConditionId: "
                                + condition.searchConditionId());
            }
        }
        DesiredRealtimeCondition best = matchedConditions.getFirst();
        if (effectivePriority != best.priority()
                || effectiveScreeningScore != best.screeningScore()) {
            throw new IllegalArgumentException(
                    "effective rank must match the first condition");
        }
    }

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
    }
}
