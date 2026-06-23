package com.stockapp.external.kis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class KisWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("KIS WebSocket 연결 성공 - sessionId: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        if (isPingPongMessage(payload)) {
            log.debug("KIS WebSocket PINGPONG 수신");
            return;
        }

        if (isJsonMessage(payload)) {
            log.info("KIS WebSocket JSON 응답 수신: {}", payload);
            return;
        }

        if (isRealtimeTradeMessage(payload)) {
            log.info("===== KIS 실시간 체결 메시지 시작 =====");
            log.info("{}", payload);
            log.info("===== KIS 실시간 체결 메시지 끝 =====");
            return;
        }

        log.info("KIS WebSocket 알 수 없는 메시지 수신: {}", payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("KIS WebSocket 오류 발생 - sessionId: {}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        log.warn("KIS WebSocket 연결 종료 - sessionId: {}, status: {}", session.getId(), status);
    }

    private boolean isPingPongMessage(String payload) {
        return payload != null && payload.contains("PINGPONG");
    }

    private boolean isJsonMessage(String payload) {
        return payload != null && payload.trim().startsWith("{");
    }

    private boolean isRealtimeTradeMessage(String payload) {
        return payload != null && payload.startsWith("0|H0STCNT0|");
    }
}