package com.stockapp.external.kis;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KisWebSocketSubscriptionTracker {

    private final ConcurrentHashMap<Key, Entry> entries = new ConcurrentHashMap<>();

    public void registerPending(String sessionId, String trId, String stockCode,
                                KisWebSocketOperation operation) {
        Key key = new Key(sessionId, trId, stockCode, operation);
        if (entries.putIfAbsent(key, new Entry(key)) != null) {
            throw new IllegalStateException("subscription request is already registered");
        }
    }

    public boolean handle(String sessionId, KisWebSocketControlResponse response) {
        Entry entry = find(sessionId, response.trId(), response.trKey());
        if (entry == null) {
            return false;
        }
        KisSubscriptionStatus status = response.isSuccess()
                ? KisSubscriptionStatus.CONFIRMED : KisSubscriptionStatus.FAILED;
        return entry.complete(new KisWebSocketSubscriptionResult(
                sessionId, response.trId(), response.trKey(), entry.key.operation(),
                status, response.messageCode(), response.message()));
    }

    public KisWebSocketSubscriptionResult awaitResult(
            String sessionId, String trId, String stockCode,
            KisWebSocketOperation operation, Duration timeout) throws InterruptedException {
        Key key = new Key(sessionId, trId, stockCode, operation);
        Entry entry = entries.get(key);
        if (entry == null) {
            throw new IllegalStateException("subscription request is not registered");
        }
        try {
            return entry.result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            entry.complete(failed(key, null, "ACK_TIMEOUT"));
            return entry.result.join();
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("subscription result completion failed", exception);
        }
    }

    public void failPendingForSession(String sessionId, String messageCode, String message) {
        entries.values().stream()
                .filter(entry -> entry.key.sessionId().equals(sessionId))
                .forEach(entry -> entry.complete(failed(entry.key, messageCode, message)));
    }

    public List<KisWebSocketSubscriptionResult> snapshot(String sessionId) {
        return entries.values().stream()
                .filter(entry -> entry.key.sessionId().equals(sessionId))
                .map(Entry::snapshot)
                .sorted(java.util.Comparator.comparing(
                        KisWebSocketSubscriptionResult::stockCode))
                .toList();
    }

    private Entry find(String sessionId, String trId, String stockCode) {
        return entries.values().stream()
                .filter(entry -> entry.key.sessionId().equals(sessionId))
                .filter(entry -> entry.key.trId().equals(trId))
                .filter(entry -> entry.key.stockCode().equals(stockCode))
                .filter(entry -> !entry.result.isDone())
                .findFirst().orElse(null);
    }

    private KisWebSocketSubscriptionResult failed(Key key, String code, String message) {
        return new KisWebSocketSubscriptionResult(
                key.sessionId(), key.trId(), key.stockCode(), key.operation(),
                KisSubscriptionStatus.FAILED, code, message);
    }

    private record Key(String sessionId, String trId, String stockCode,
                       KisWebSocketOperation operation) {
        private Key {
            Objects.requireNonNull(sessionId, "sessionId is required");
            Objects.requireNonNull(trId, "trId is required");
            Objects.requireNonNull(stockCode, "stockCode is required");
            Objects.requireNonNull(operation, "operation is required");
        }
    }

    private static final class Entry {
        private final Key key;
        private final CompletableFuture<KisWebSocketSubscriptionResult> result =
                new CompletableFuture<>();

        private Entry(Key key) {
            this.key = key;
        }

        private boolean complete(KisWebSocketSubscriptionResult value) {
            return result.complete(value);
        }

        private KisWebSocketSubscriptionResult snapshot() {
            return result.getNow(new KisWebSocketSubscriptionResult(
                    key.sessionId(), key.trId(), key.stockCode(), key.operation(),
                    KisSubscriptionStatus.PENDING, null, null));
        }
    }
}
