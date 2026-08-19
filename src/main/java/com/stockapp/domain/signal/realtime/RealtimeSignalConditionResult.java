package com.stockapp.domain.signal.realtime;

import java.util.Objects;

public record RealtimeSignalConditionResult(
        Long conditionId,
        boolean matched
) {
    public RealtimeSignalConditionResult {
        Objects.requireNonNull(conditionId, "conditionId is required");
    }
}
