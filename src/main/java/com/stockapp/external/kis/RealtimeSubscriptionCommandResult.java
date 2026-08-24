package com.stockapp.external.kis;

import java.util.Objects;

public record RealtimeSubscriptionCommandResult(
        RealtimeSubscriptionCommandOperation operation,
        String stockCode,
        RealtimeSubscriptionCommandStatus status,
        boolean activeAfter
) {

    public RealtimeSubscriptionCommandResult {
        Objects.requireNonNull(operation, "operation is required");
        Objects.requireNonNull(stockCode, "stockCode is required");
        Objects.requireNonNull(status, "status is required");
        if (stockCode.isBlank()) {
            throw new IllegalArgumentException(
                    "stockCode must not be blank");
        }
    }
}
