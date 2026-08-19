package com.stockapp.external.kis;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class KisWebSocketSessionManager {

    private final KisWebSocketClient kisWebSocketClient;
    private final AtomicReference<KisWebSocketSession> session =
            new AtomicReference<>();

    public KisWebSocketSessionManager(KisWebSocketClient kisWebSocketClient) {
        this.kisWebSocketClient = kisWebSocketClient;
    }

    public synchronized void connectAll(List<String> stockCodes) {
        List<String> uniqueStockCodes = validateAndCopy(stockCodes);
        if (session.get() != null) {
            throw new IllegalStateException(
                    "KIS WebSocket sessions are already active");
        }
        if (uniqueStockCodes.isEmpty()) {
            return;
        }

        KisWebSocketSession connectedSession = Objects.requireNonNull(
                kisWebSocketClient.connectAndSubscribe(uniqueStockCodes),
                "connected session is required");
        session.set(connectedSession);
    }

    public List<KisWebSocketSession> sessions() {
        KisWebSocketSession current = session.get();
        return current == null ? List.of() : List.of(current);
    }

    public int sessionCount() {
        return session.get() == null ? 0 : 1;
    }

    public boolean isEmpty() {
        return session.get() == null;
    }

    public synchronized void closeAll() throws IOException {
        KisWebSocketSession sessionToClose = session.getAndSet(null);
        if (sessionToClose != null) {
            sessionToClose.close();
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

}
