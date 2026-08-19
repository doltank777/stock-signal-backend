package com.stockapp.domain.signal.realtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record RealtimeSignalEvaluationResult(
        Long stockId,
        String stockCode,
        LocalDateTime tradeDateTime,
        List<RealtimeSignalConditionResult> conditionResults
) {
    public RealtimeSignalEvaluationResult {
        Objects.requireNonNull(stockId, "stockId is required");
        Objects.requireNonNull(stockCode, "stockCode is required");
        Objects.requireNonNull(tradeDateTime, "tradeDateTime is required");
        conditionResults = List.copyOf(Objects.requireNonNull(
                conditionResults, "conditionResults are required"));
    }
}
