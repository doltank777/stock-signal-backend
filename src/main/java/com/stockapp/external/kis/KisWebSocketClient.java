package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisWebSocketClient {

    private static final String TR_ID_REALTIME_PRICE = "H0STCNT0";
    private static final String CUSTOMER_TYPE_PERSONAL = "P";
    private static final String TRANSACTION_TYPE_SUBSCRIBE = "1";
    private static final String CONTENT_TYPE_UTF8 = "utf-8";

    private final KisProperties kisProperties;
    private final KisWebSocketApprovalClient kisWebSocketApprovalClient;
    private final KisWebSocketHandler kisWebSocketHandler;

    public void connectAndSubscribe(List<String> stockCodes) {
        try {
            String approvalKey = kisWebSocketApprovalClient.getApprovalKey();

            StandardWebSocketClient webSocketClient = new StandardWebSocketClient();

            WebSocketSession session = webSocketClient
                    .execute(
                            kisWebSocketHandler,
                            null,
                            URI.create(kisProperties.getWebSocketUrl())
                    )
                    .get();

            log.info("KIS WebSocket 단일 세션 연결 완료 - 구독 대상 종목 수: {}", stockCodes.size());

            for (String stockCode : stockCodes) {
                String subscribeMessage = createSubscribeMessage(approvalKey, stockCode);
                session.sendMessage(new TextMessage(subscribeMessage));

                log.info("KIS WebSocket 구독 요청 완료 - stockCode: {}", stockCode);

                Thread.sleep(300);
            }

        } catch (Exception e) {
            log.error("KIS WebSocket 연결 또는 다중 구독 실패", e);
        }
    }

    public void connectAndSubscribe(String stockCode) {
        connectAndSubscribe(List.of(stockCode));
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