package com.stockapp.external.kis;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class KisWebSocketSubscriptionTracker {

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Entry> closingEntries =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedHashSet<String>> activeStockCodes =
            new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public KisWebSocketSubscriptionRequest registerPending(
            String sessionId, String trId, String stockCode,
            KisWebSocketOperation operation) {
        if (findPending(sessionId, trId, stockCode) != null) {
            throw new IllegalStateException("subscription request is already registered");
        }
        long requestSequence = sequence.getAndIncrement();
        KisWebSocketSubscriptionRequest request =
                new KisWebSocketSubscriptionRequest(
                        Objects.requireNonNull(sessionId, "sessionId is required"),
                        Objects.requireNonNull(trId, "trId is required"),
                        Objects.requireNonNull(stockCode, "stockCode is required"),
                        Objects.requireNonNull(operation, "operation is required"),
                        requestSequence);
        entries.put(requestSequence, new Entry(request));
        return request;
    }

    public boolean handle(String sessionId, KisWebSocketControlResponse response) {
        Entry entry = findPending(sessionId, response.trId(), response.trKey());
        if (entry == null && response.isAckLike()) {
            entry = findUniqueCompatiblePending(sessionId, response);
        }
        if (entry == null) {
            return false;
        }
        KisSubscriptionStatus status = response.isSuccess()
                ? KisSubscriptionStatus.CONFIRMED : KisSubscriptionStatus.FAILED;
        boolean completed = entry.complete(new KisWebSocketSubscriptionResult(
                sessionId, entry.request.trId(), entry.request.stockCode(),
                entry.request.operation(),
                status, response.messageCode(), response.message()));
        if (completed && status == KisSubscriptionStatus.CONFIRMED) {
            updateActive(entry.request);
        }
        return completed;
    }

    public KisWebSocketSubscriptionResult awaitResult(
            String sessionId, String trId, String stockCode,
            KisWebSocketOperation operation, Duration timeout) throws InterruptedException {
        Entry entry = entries.values().stream()
                .filter(candidate -> candidate.request.sessionId().equals(sessionId))
                .filter(candidate -> candidate.request.trId().equals(trId))
                .filter(candidate -> candidate.request.stockCode().equals(stockCode))
                .filter(candidate -> candidate.request.operation() == operation)
                .max(java.util.Comparator.comparingLong(
                        candidate -> candidate.request.sequence()))
                .orElse(null);
        if (entry == null) {
            throw new IllegalStateException("subscription request is not registered");
        }
        try {
            return entry.result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            entry.complete(failed(entry.request, null, "ACK_TIMEOUT"));
            return entry.result.join();
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("subscription result completion failed", exception);
        }
    }

    public KisWebSocketSubscriptionResult awaitResult(
            KisWebSocketSubscriptionRequest request,
            Duration timeout
    ) throws InterruptedException {
        Objects.requireNonNull(request, "request is required");
        Entry entry = entries.get(request.sequence());
        if (entry == null) {
            entry = closingEntries.get(request.sequence());
        }
        if (entry == null) {
            throw new IllegalStateException(
                    "subscription request is not registered");
        }
        try {
            return awaitEntry(entry, timeout);
        } finally {
            closingEntries.remove(request.sequence(), entry);
        }
    }

    public void failPendingForSession(String sessionId, String messageCode, String message) {
        entries.values().stream()
                .filter(entry -> entry.request.sessionId().equals(sessionId))
                .forEach(entry -> entry.complete(failed(
                        entry.request, messageCode, message)));
    }

    public void clearSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        entries.forEach((sequence, entry) -> {
            if (!entry.request.sessionId().equals(sessionId)) {
                return;
            }
            boolean wasPending = !entry.result.isDone();
            if (wasPending) {
                entry.complete(failed(
                        entry.request, "CONNECTION_CLOSED", "session closed"));
                closingEntries.put(sequence, entry);
            }
            entries.remove(sequence, entry);
        });
        activeStockCodes.remove(sessionId);
    }

    public void discard(KisWebSocketSubscriptionRequest request) {
        Objects.requireNonNull(request, "request is required");
        entries.remove(request.sequence());
        closingEntries.remove(request.sequence());
    }

    public List<KisWebSocketSubscriptionResult> snapshot(String sessionId) {
        return entries.values().stream()
                .filter(entry -> entry.request.sessionId().equals(sessionId))
                .sorted(java.util.Comparator.comparingLong(Entry::sequence))
                .map(Entry::snapshot)
                .toList();
    }

    public List<String> activeStockCodes(String sessionId) {
        LinkedHashSet<String> active = activeStockCodes.get(sessionId);
        if (active == null) {
            return List.of();
        }
        synchronized (active) {
            return List.copyOf(active);
        }
    }

    private Entry findPending(String sessionId, String trId, String stockCode) {
        return entries.values().stream()
                .filter(entry -> entry.request.sessionId().equals(sessionId))
                .filter(entry -> entry.request.trId().equals(trId))
                .filter(entry -> entry.request.stockCode().equals(stockCode))
                .filter(entry -> !entry.result.isDone())
                .findFirst().orElse(null);
    }

    private Entry findUniqueCompatiblePending(
            String sessionId,
            KisWebSocketControlResponse response
    ) {
        List<Entry> sessionPending = entries.values().stream()
                .filter(entry -> entry.request.sessionId().equals(sessionId))
                .filter(entry -> !entry.result.isDone())
                .toList();
        if (!hasText(response.trId()) && sessionPending.size() != 1) {
            return null;
        }
        List<Entry> compatible = sessionPending.stream()
                .filter(entry -> !hasText(response.trId())
                        || entry.request.trId().equals(response.trId()))
                .filter(entry -> !hasText(response.trKey())
                        || entry.request.stockCode().equals(response.trKey()))
                .toList();
        return compatible.size() == 1 ? compatible.getFirst() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void updateActive(KisWebSocketSubscriptionRequest request) {
        LinkedHashSet<String> active = activeStockCodes.computeIfAbsent(
                request.sessionId(), ignored -> new LinkedHashSet<>());
        synchronized (active) {
            if (request.operation() == KisWebSocketOperation.SUBSCRIBE) {
                active.add(request.stockCode());
            } else {
                active.remove(request.stockCode());
            }
        }
    }

    private KisWebSocketSubscriptionResult failed(
            KisWebSocketSubscriptionRequest request,
            String code,
            String message) {
        return new KisWebSocketSubscriptionResult(
                request.sessionId(), request.trId(), request.stockCode(), request.operation(),
                KisSubscriptionStatus.FAILED, code, message);
    }

    private KisWebSocketSubscriptionResult awaitEntry(
            Entry entry,
            Duration timeout
    ) throws InterruptedException {
        try {
            return entry.result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            entry.complete(failed(entry.request, null, "ACK_TIMEOUT"));
            return entry.result.join();
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException(
                    "subscription result completion failed", exception);
        }
    }

    private static final class Entry {
        private final KisWebSocketSubscriptionRequest request;
        private final CompletableFuture<KisWebSocketSubscriptionResult> result =
                new CompletableFuture<>();

        private Entry(KisWebSocketSubscriptionRequest request) {
            this.request = request;
        }

        private long sequence() {
            return request.sequence();
        }

        private boolean complete(KisWebSocketSubscriptionResult value) {
            return result.complete(value);
        }

        private KisWebSocketSubscriptionResult snapshot() {
            return result.getNow(new KisWebSocketSubscriptionResult(
                    request.sessionId(), request.trId(), request.stockCode(),
                    request.operation(),
                    KisSubscriptionStatus.PENDING, null, null));
        }
    }
}
