package com.stockapp.external.kis;

public record KisWebSocketSubscriptionResult(
        String sessionId,
        String trId,
        String stockCode,
        KisWebSocketOperation operation,
        KisSubscriptionStatus status,
        String messageCode,
        String message
) {
}
