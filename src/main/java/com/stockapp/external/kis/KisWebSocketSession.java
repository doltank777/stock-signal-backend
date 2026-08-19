package com.stockapp.external.kis;

import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class KisWebSocketSession {

    private final WebSocketSession session;
    private final List<String> requestedStockCodes;
    private final KisWebSocketSubscriptionTracker subscriptionTracker;
    private final String approvalKey;

    KisWebSocketSession(WebSocketSession session, List<String> requestedStockCodes) {
        this(session, requestedStockCodes,
                new KisWebSocketSubscriptionTracker(), null);
    }

    KisWebSocketSession(
            WebSocketSession session,
            List<String> requestedStockCodes,
            KisWebSocketSubscriptionTracker subscriptionTracker
    ) {
        this(session, requestedStockCodes, subscriptionTracker, null);
    }

    KisWebSocketSession(
            WebSocketSession session,
            List<String> requestedStockCodes,
            KisWebSocketSubscriptionTracker subscriptionTracker,
            String approvalKey
    ) {
        this.session = Objects.requireNonNull(
                session, "session is required");
        this.requestedStockCodes = validateAndCopy(requestedStockCodes);
        this.subscriptionTracker = Objects.requireNonNull(
                subscriptionTracker, "subscriptionTracker is required");
        this.approvalKey = approvalKey;
    }

    public String sessionId() {
        return session.getId();
    }

    public List<String> requestedStockCodes() {
        return requestedStockCodes;
    }

    public List<String> confirmedStockCodes() {
        return stockCodesWithStatus(KisSubscriptionStatus.CONFIRMED);
    }

    public List<String> failedStockCodes() {
        return stockCodesWithStatus(KisSubscriptionStatus.FAILED);
    }

    public List<KisWebSocketSubscriptionResult> subscriptionResults() {
        return subscriptionTracker.snapshot(sessionId());
    }

    public List<String> activeStockCodes() {
        return subscriptionTracker.activeStockCodes(sessionId());
    }

    WebSocketSession webSocketSession() {
        return session;
    }

    String approvalKey() {
        return Objects.requireNonNull(
                approvalKey, "approvalKey is not available");
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    public void close() throws IOException {
        if (session.isOpen()) {
            session.close();
        }
    }

    private List<String> stockCodesWithStatus(KisSubscriptionStatus status) {
        return subscriptionResults().stream()
                .filter(result -> result.operation() == KisWebSocketOperation.SUBSCRIBE)
                .filter(result -> result.status() == status)
                .map(KisWebSocketSubscriptionResult::stockCode)
                .toList();
    }

    private List<String> validateAndCopy(List<String> stockCodes) {
        Objects.requireNonNull(stockCodes, "requestedStockCodes are required");
        LinkedHashSet<String> uniqueStockCodes = new LinkedHashSet<>();
        for (String stockCode : stockCodes) {
            Objects.requireNonNull(stockCode, "stockCode is required");
            if (stockCode.isBlank()) {
                throw new IllegalArgumentException(
                        "stockCode must not be blank");
            }
            uniqueStockCodes.add(stockCode);
        }
        if (uniqueStockCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one requestedStockCode is required");
        }
        return List.copyOf(uniqueStockCodes);
    }
}
