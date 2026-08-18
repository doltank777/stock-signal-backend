package com.stockapp.external.kis;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class KisWebSocketSessionManager {

    static final int MAX_STOCKS_PER_SESSION = 40;
    private static final long SESSION_CONNECT_INTERVAL_MILLIS = 1_000L;

    private final KisWebSocketClient kisWebSocketClient;
    private final KisWebSocketSessionSleeper sessionSleeper;
    private final AtomicReference<List<KisWebSocketSession>> sessions =
            new AtomicReference<>(List.of());

    public KisWebSocketSessionManager(
            KisWebSocketClient kisWebSocketClient,
            KisWebSocketSessionSleeper sessionSleeper
    ) {
        this.kisWebSocketClient = kisWebSocketClient;
        this.sessionSleeper = sessionSleeper;
    }

    public synchronized void connectAll(List<String> stockCodes) {
        List<String> uniqueStockCodes = validateAndCopy(stockCodes);
        if (!sessions.get().isEmpty()) {
            throw new IllegalStateException(
                    "KIS WebSocket sessions are already active");
        }
        if (uniqueStockCodes.isEmpty()) {
            return;
        }

        List<List<String>> chunks = partition(uniqueStockCodes);
        List<KisWebSocketSession> connectedSessions = new ArrayList<>();
        try {
            for (int index = 0; index < chunks.size(); index++) {
                connectedSessions.add(Objects.requireNonNull(
                        kisWebSocketClient.connectAndSubscribe(
                                chunks.get(index)),
                        "connected session is required"));
                if (index + 1 < chunks.size()) {
                    sessionSleeper.sleep(
                            SESSION_CONNECT_INTERVAL_MILLIS);
                }
            }
        } catch (Exception connectionFailure) {
            restoreInterrupt(connectionFailure);
            closeForRollback(connectedSessions, connectionFailure);
            if (connectionFailure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new KisWebSocketException(
                    "KIS WebSocket 세션 연결 간격 대기에 실패했습니다.",
                    connectionFailure);
        }

        sessions.set(List.copyOf(connectedSessions));
    }

    public List<KisWebSocketSession> sessions() {
        return sessions.get();
    }

    public int sessionCount() {
        return sessions.get().size();
    }

    public boolean isEmpty() {
        return sessions.get().isEmpty();
    }

    public synchronized void closeAll() throws IOException {
        List<KisWebSocketSession> sessionsToClose =
                sessions.getAndSet(List.of());
        IOException firstFailure = null;
        for (KisWebSocketSession session : sessionsToClose) {
            try {
                session.close();
            } catch (IOException closeFailure) {
                if (firstFailure == null) {
                    firstFailure = closeFailure;
                } else {
                    firstFailure.addSuppressed(closeFailure);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @PreDestroy
    void closeOnShutdown() {
        try {
            closeAll();
        } catch (IOException closeFailure) {
            log.warn("KIS WebSocket 세션 종료 중 오류가 발생했습니다.",
                    closeFailure);
        }
    }

    private List<String> validateAndCopy(List<String> stockCodes) {
        Objects.requireNonNull(stockCodes, "stockCodes are required");
        LinkedHashSet<String> uniqueStockCodes = new LinkedHashSet<>();
        for (String stockCode : stockCodes) {
            Objects.requireNonNull(stockCode, "stockCode is required");
            if (stockCode.isBlank()) {
                throw new IllegalArgumentException(
                        "stockCode must not be blank");
            }
            uniqueStockCodes.add(stockCode);
        }
        return List.copyOf(uniqueStockCodes);
    }

    private List<List<String>> partition(List<String> stockCodes) {
        List<List<String>> chunks = new ArrayList<>();
        for (int start = 0;
             start < stockCodes.size();
             start += MAX_STOCKS_PER_SESSION) {
            int end = Math.min(
                    start + MAX_STOCKS_PER_SESSION,
                    stockCodes.size());
            chunks.add(stockCodes.subList(start, end));
        }
        return chunks;
    }

    private void closeForRollback(
            List<KisWebSocketSession> connectedSessions,
            Exception connectionFailure
    ) {
        for (KisWebSocketSession session : connectedSessions) {
            try {
                session.close();
            } catch (IOException closeFailure) {
                connectionFailure.addSuppressed(closeFailure);
            }
        }
    }

    private void restoreInterrupt(Exception exception) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
