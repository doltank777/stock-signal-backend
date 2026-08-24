package com.stockapp.external.kis;

import com.stockapp.domain.screening.realtime.RealtimeWatchPolicy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class ManagedRealtimeSubscriptionGateway {

    private final KisWebSocketSessionManager sessionManager;
    private final KisWebSocketClient webSocketClient;

    public ManagedRealtimeSubscriptionGateway(
            KisWebSocketSessionManager sessionManager,
            KisWebSocketClient webSocketClient
    ) {
        this.sessionManager = sessionManager;
        this.webSocketClient = webSocketClient;
    }

    public Set<String> currentActiveStockCodes() {
        return sessionManager.currentOpenSession()
                .map(this::activeSnapshot)
                .orElseGet(Set::of);
    }

    public RealtimeSubscriptionCommandResult subscribe(String stockCode) {
        String normalizedCode = validateStockCode(stockCode);
        KisWebSocketSession session = requireOpenSession();
        synchronized (session.commandMonitor()) {
            requireStillOpen(session);
            Set<String> active = activeSnapshot(session);
            if (active.contains(normalizedCode)) {
                return result(
                        RealtimeSubscriptionCommandOperation.SUBSCRIBE,
                        normalizedCode,
                        RealtimeSubscriptionCommandStatus.ALREADY_ACTIVE,
                        true);
            }
            if (active.size() >= RealtimeWatchPolicy.CAPACITY) {
                throw new RealtimeSubscriptionCapacityExceededException(
                        RealtimeWatchPolicy.CAPACITY,
                        active.size(), normalizedCode);
            }
            webSocketClient.subscribe(session, normalizedCode);
            if (!activeSnapshot(session).contains(normalizedCode)) {
                throw new IllegalStateException(
                        "confirmed subscribe is missing from physical tracker: "
                                + normalizedCode);
            }
            return result(
                    RealtimeSubscriptionCommandOperation.SUBSCRIBE,
                    normalizedCode,
                    RealtimeSubscriptionCommandStatus.APPLIED,
                    true);
        }
    }

    public RealtimeSubscriptionCommandResult unsubscribe(String stockCode) {
        String normalizedCode = validateStockCode(stockCode);
        KisWebSocketSession session = requireOpenSession();
        synchronized (session.commandMonitor()) {
            requireStillOpen(session);
            if (!activeSnapshot(session).contains(normalizedCode)) {
                return result(
                        RealtimeSubscriptionCommandOperation.UNSUBSCRIBE,
                        normalizedCode,
                        RealtimeSubscriptionCommandStatus.ALREADY_INACTIVE,
                        false);
            }
            webSocketClient.unsubscribe(session, normalizedCode);
            if (activeSnapshot(session).contains(normalizedCode)) {
                throw new IllegalStateException(
                        "confirmed unsubscribe remains in physical tracker: "
                                + normalizedCode);
            }
            return result(
                    RealtimeSubscriptionCommandOperation.UNSUBSCRIBE,
                    normalizedCode,
                    RealtimeSubscriptionCommandStatus.APPLIED,
                    false);
        }
    }

    private Set<String> activeSnapshot(KisWebSocketSession session) {
        return Set.copyOf(new LinkedHashSet<>(session.activeStockCodes()));
    }

    private KisWebSocketSession requireOpenSession() {
        return sessionManager.currentOpenSession().orElseThrow(
                RealtimeSubscriptionSessionUnavailableException::new);
    }

    private void requireStillOpen(KisWebSocketSession session) {
        if (!session.isOpen()) {
            throw new RealtimeSubscriptionSessionUnavailableException();
        }
    }

    private String validateStockCode(String stockCode) {
        if (stockCode == null) {
            throw new NullPointerException("stockCode is required");
        }
        String normalized = stockCode.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "stockCode must not be blank");
        }
        return normalized;
    }

    private RealtimeSubscriptionCommandResult result(
            RealtimeSubscriptionCommandOperation operation,
            String stockCode,
            RealtimeSubscriptionCommandStatus status,
            boolean activeAfter
    ) {
        return new RealtimeSubscriptionCommandResult(
                operation, stockCode, status, activeAfter);
    }
}
