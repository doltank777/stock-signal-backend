package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class KisWebSocketClient {

    private static final String TR_ID_REALTIME_PRICE = "H0STCNT0";
    private static final String CUSTOMER_TYPE_PERSONAL = "P";
    private static final String CONTENT_TYPE_UTF8 = "utf-8";
    private static final long SUBSCRIBE_INTERVAL_MILLIS = 300L;
    private static final Duration SUBSCRIBE_ACK_TIMEOUT = Duration.ofSeconds(5);

    private final KisProperties kisProperties;
    private final KisWebSocketApprovalClient kisWebSocketApprovalClient;
    private final KisWebSocketMessageHandler kisWebSocketHandler;
    private final KisWebSocketConnector kisWebSocketConnector;
    private final KisWebSocketSubscriptionTracker subscriptionTracker;

    public KisWebSocketClient(
            KisProperties kisProperties,
            KisWebSocketApprovalClient kisWebSocketApprovalClient,
            KisWebSocketMessageHandler kisWebSocketHandler,
            KisWebSocketConnector kisWebSocketConnector,
            KisWebSocketSubscriptionTracker subscriptionTracker
    ) {
        this.kisProperties = kisProperties;
        this.kisWebSocketApprovalClient = kisWebSocketApprovalClient;
        this.kisWebSocketHandler = kisWebSocketHandler;
        this.kisWebSocketConnector = kisWebSocketConnector;
        this.subscriptionTracker = subscriptionTracker;
    }

    public KisWebSocketSession connectAndSubscribe(List<String> stockCodes) {
        List<String> requestedStockCodes = validateAndCopy(stockCodes);
        String approvalKey;
        try {
            approvalKey = kisWebSocketApprovalClient.getApprovalKey();
        } catch (RuntimeException exception) {
            throw new KisWebSocketException(
                    "KIS WebSocket approval key 조회에 실패했습니다.",
                    exception);
        }

        WebSocketSession session;
        try {
            session = kisWebSocketConnector.connect(
                    kisWebSocketHandler,
                    URI.create(kisProperties.getWebSocketUrl()));
        } catch (Exception exception) {
            restoreInterrupt(exception);
            throw new KisWebSocketException(
                    "KIS WebSocket 연결에 실패했습니다.", exception);
        }

        log.info("KIS WebSocket 세션 연결 완료 - 구독 대상 종목 수: {}", requestedStockCodes.size());

        try {
            for (int index = 0; index < requestedStockCodes.size(); index++) {
                String stockCode = requestedStockCodes.get(index);
                executeOperation(session, approvalKey, stockCode,
                        KisWebSocketOperation.SUBSCRIBE);
                log.info("KIS WebSocket subscription confirmed - stockCode: {}", stockCode);
                if (index + 1 < requestedStockCodes.size()) {
                    Thread.sleep(SUBSCRIBE_INTERVAL_MILLIS);
                }
            }
        } catch (Exception exception) {
            restoreInterrupt(exception);
            closeAfterSubscriptionFailure(session, exception);
            if (exception instanceof KisWebSocketException webSocketException) {
                throw webSocketException;
            }
            throw new KisWebSocketException("KIS WebSocket subscription failed", exception);
        }

        return new KisWebSocketSession(
                session, requestedStockCodes, subscriptionTracker, approvalKey);
    }

    public KisWebSocketSession connectAndSubscribe(String stockCode) {
        return connectAndSubscribe(List.of(stockCode));
    }

    public KisWebSocketSubscriptionResult subscribe(
            KisWebSocketSession session,
            String stockCode
    ) {
        validateOperation(session, stockCode);
        if (session.activeStockCodes().contains(stockCode.trim())) {
            throw new IllegalStateException("stockCode is already actively subscribed");
        }
        return executeOnManagedSession(
                session.webSocketSession(), session.approvalKey(),
                stockCode.trim(), KisWebSocketOperation.SUBSCRIBE);
    }

    public KisWebSocketSubscriptionResult unsubscribe(
            KisWebSocketSession session,
            String stockCode
    ) {
        validateOperation(session, stockCode);
        if (!session.activeStockCodes().contains(stockCode.trim())) {
            throw new IllegalStateException("stockCode is not actively subscribed");
        }
        return executeOnManagedSession(
                session.webSocketSession(), session.approvalKey(),
                stockCode.trim(), KisWebSocketOperation.UNSUBSCRIBE);
    }

    private KisWebSocketSubscriptionResult executeOperation(
            WebSocketSession session,
            String approvalKey,
            String stockCode,
            KisWebSocketOperation operation
    ) throws IOException, InterruptedException {
        KisWebSocketSubscriptionRequest request =
                subscriptionTracker.registerPending(
                        session.getId(), TR_ID_REALTIME_PRICE,
                        stockCode, operation);
        KisWebSocketSubscriptionResult result;
        try {
            session.sendMessage(new TextMessage(createSubscriptionMessage(
                    approvalKey, stockCode, operation)));
            result = subscriptionTracker.awaitResult(
                    request, SUBSCRIBE_ACK_TIMEOUT);
        } catch (IOException | InterruptedException exception) {
            subscriptionTracker.discard(request);
            throw exception;
        }
        if (result.status() != KisSubscriptionStatus.CONFIRMED) {
            throw rejected(result);
        }
        return result;
    }

    private KisWebSocketSubscriptionResult executeOnManagedSession(
            WebSocketSession session,
            String approvalKey,
            String stockCode,
            KisWebSocketOperation operation
    ) {
        try {
            return executeOperation(session, approvalKey, stockCode, operation);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new KisWebSocketException(
                    "KIS WebSocket operation interrupted", exception);
        } catch (IOException exception) {
            throw new KisWebSocketException(
                    "KIS WebSocket operation send failed", exception);
        }
    }

    private KisWebSocketException rejected(
            KisWebSocketSubscriptionResult result) {
        return new KisWebSocketException(
                "KIS WebSocket operation rejected - operation: %s, stockCode: %s, trId: %s, messageCode: %s, message: %s"
                        .formatted(result.operation(), result.stockCode(),
                                result.trId(), result.messageCode(), result.message()),
                result);
    }

    private void validateOperation(
            KisWebSocketSession session,
            String stockCode
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(stockCode, "stockCode is required");
        if (stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode must not be blank");
        }
        if (!session.isOpen()) {
            throw new IllegalStateException("KIS WebSocket session is closed");
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
        if (uniqueStockCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one stockCode is required");
        }
        return List.copyOf(uniqueStockCodes);
    }

    private void closeAfterSubscriptionFailure(
            WebSocketSession session,
            Exception subscriptionFailure
    ) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (IOException closeFailure) {
            subscriptionFailure.addSuppressed(closeFailure);
        }
    }

    private void restoreInterrupt(Exception exception) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    String createSubscriptionMessage(
            String approvalKey,
            String stockCode,
            KisWebSocketOperation operation
    ) {
        return """
                {
                  "header": {
                    "approval_key": "%s",
                    "custtype": "%s",
                    "tr_type": "%s",
                    "content-type": "%s"
                  },
                  "body": {
                    "input": {
                      "tr_id": "%s",
                      "tr_key": "%s"
                    }
                  }
                }
                """.formatted(
                approvalKey,
                CUSTOMER_TYPE_PERSONAL,
                operation.transactionType(),
                CONTENT_TYPE_UTF8,
                TR_ID_REALTIME_PRICE,
                stockCode
        );
    }
}
