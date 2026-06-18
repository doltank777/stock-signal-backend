package com.stockapp.external.kis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
public class KisWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("KIS WebSocket 연결 성공 - sessionId: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        if (payload.contains("PINGPONG")) {
            log.debug("KIS WebSocket PINGPONG 수신");
            return;
        }

        log.info("KIS WebSocket 메시지 수신: {}", payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("KIS WebSocket 오류 발생 - sessionId: {}", session.getId(), exception);
    }
}