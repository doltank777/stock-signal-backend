package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class KisWebSocketClient {

    private static final String TR_ID_REALTIME_PRICE = "H0STCNT0";
    private static final String CUSTOMER_TYPE_PERSONAL = "P";
    private static final String TRANSACTION_TYPE_SUBSCRIBE = "1";
    private static final String CONTENT_TYPE_UTF8 = "utf-8";
    private static final long SUBSCRIBE_INTERVAL_MILLIS = 300L;

    private final KisProperties kisProperties;
    private final KisWebSocketApprovalClient kisWebSocketApprovalClient;
    private final KisWebSocketHandler kisWebSocketHandler;
    private final KisWebSocketConnector kisWebSocketConnector;

    public KisWebSocketClient(
            KisProperties kisProperties,
            KisWebSocketApprovalClient kisWebSocketApprovalClient,
            KisWebSocketHandler kisWebSocketHandler,
            KisWebSocketConnector kisWebSocketConnector
    ) {
        this.kisProperties = kisProperties;
        this.kisWebSocketApprovalClient = kisWebSocketApprovalClient;
        this.kisWebSocketHandler = kisWebSocketHandler;
        this.kisWebSocketConnector = kisWebSocketConnector;
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
            for (String stockCode : requestedStockCodes) {
                String subscribeMessage = createSubscribeMessage(approvalKey, stockCode);
                session.sendMessage(new TextMessage(subscribeMessage));

                log.info("KIS WebSocket 구독 요청 완료 - stockCode: {}", stockCode);

                Thread.sleep(SUBSCRIBE_INTERVAL_MILLIS);
            }
        } catch (Exception exception) {
            restoreInterrupt(exception);
            closeAfterSubscriptionFailure(session, exception);
            throw new KisWebSocketException(
                    "KIS WebSocket 구독 요청에 실패했습니다.", exception);
        }

        return new KisWebSocketSession(session, requestedStockCodes);
    }

    public KisWebSocketSession connectAndSubscribe(String stockCode) {
        return connectAndSubscribe(List.of(stockCode));
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

    private String createSubscribeMessage(String approvalKey, String stockCode) {
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
                TRANSACTION_TYPE_SUBSCRIBE,
                CONTENT_TYPE_UTF8,
                TR_ID_REALTIME_PRICE,
                stockCode
        );
    }
}
