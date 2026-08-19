package com.stockapp.external.kis.probe;

import com.stockapp.external.kis.KisWebSocketControlResponseParser;
import com.stockapp.external.kis.KisWebSocketMessageHandler;
import com.stockapp.external.kis.KisWebSocketSubscriptionTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@Profile("kis-websocket-probe")
@RequiredArgsConstructor
public class KisWebSocketProbeHandler extends TextWebSocketHandler
        implements KisWebSocketMessageHandler {

    private final KisWebSocketControlResponseParser controlResponseParser;
    private final KisWebSocketSubscriptionTracker subscriptionTracker;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();
        if (payload != null && payload.contains("PINGPONG")) {
            session.sendMessage(message);
            return;
        }
        if (payload == null || !payload.trim().startsWith("{")) {
            log.debug("Ignoring KIS realtime data in WebSocket probe - sessionId: {}",
                    session.getId());
            return;
        }
        controlResponseParser.parse(payload).ifPresentOrElse(response -> {
            if (!subscriptionTracker.handle(session.getId(), response)) {
                log.warn("Uncorrelated probe control response - sessionId: {}, trId: {}, trKey: {}",
                        session.getId(), response.trId(), response.trKey());
            }
        }, () -> log.warn("Malformed probe control response - sessionId: {}",
                session.getId()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        subscriptionTracker.failPendingForSession(
                session.getId(), "TRANSPORT_ERROR", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptionTracker.failPendingForSession(
                session.getId(), "CONNECTION_CLOSED", status.toString());
    }
}
