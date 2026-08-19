package com.stockapp.external.kis;

public record KisWebSocketSubscriptionRequest(
        String sessionId,
        String trId,
        String stockCode,
        KisWebSocketOperation operation,
        long sequence
) {
}
