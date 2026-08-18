package com.stockapp.domain.screening.realtime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record RealtimeWatchTarget(
        Long stockId,
        String stockCode,
        List<Long> conditionIds
) {

    public RealtimeWatchTarget {
        Objects.requireNonNull(stockId, "stockId is required");
        Objects.requireNonNull(stockCode, "stockCode is required");
        if (stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }

        Objects.requireNonNull(conditionIds, "conditionIds are required");
        LinkedHashSet<Long> uniqueConditionIds = new LinkedHashSet<>();
        for (Long conditionId : conditionIds) {
            uniqueConditionIds.add(Objects.requireNonNull(
                    conditionId, "conditionId is required"));
        }
        if (uniqueConditionIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one conditionId is required");
        }
        conditionIds = List.copyOf(uniqueConditionIds);
    }
}
