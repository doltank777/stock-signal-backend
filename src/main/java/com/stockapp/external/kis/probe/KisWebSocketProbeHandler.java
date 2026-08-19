package com.stockapp.external.kis.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Profile("kis-websocket-probe")
@RequiredArgsConstructor
public class KisWebSocketProbeHandler extends TextWebSocketHandler
        implements KisWebSocketMessageHandler {

    private static final int MAX_DIAGNOSTIC_PAYLOAD_LENGTH = 1500;
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "approval_key", "appkey", "appsecret", "authorization", "token");

    private final KisWebSocketControlResponseParser controlResponseParser;
    private final KisWebSocketSubscriptionTracker subscriptionTracker;
    private final ObjectMapper objectMapper;
    private final KisWebSocketProbeProperties properties;

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
                logDiagnostic("PARSED_BUT_UNCORRELATED", session, payload,
                        response.trId(), response.trKey());
            }
        }, () -> logDiagnostic(
                "PARSE_FAILED", session, payload, null, null));
    }

    private void logDiagnostic(
            String outcome,
            WebSocketSession session,
            String payload,
            String parsedTrId,
            String parsedTrKey
    ) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode header = root.path("header");
            JsonNode body = root.path("body");
            log.warn("Probe control response diagnostic - outcome: {}, sessionId: {}, "
                            + "rootFields: {}, headerFields: {}, bodyFields: {}, "
                            + "trId: {}, trKey: {}, rtCd: {}, msgCd: {}, msg1: {}",
                    outcome, session.getId(), fieldNames(root), fieldNames(header),
                    fieldNames(body), value(header, "tr_id", parsedTrId),
                    value(header, "tr_key", parsedTrKey), value(body, "rt_cd", null),
                    value(body, "msg_cd", null), value(body, "msg1", null));
            if (properties.isLogControlPayload()) {
                log.warn("Probe control response sanitized payload - outcome: {}, "
                                + "sessionId: {}, payload: {}",
                        outcome, session.getId(), sanitizedPayload(root));
            }
        } catch (Exception exception) {
            log.warn("Probe control response diagnostic - outcome: {}, sessionId: {}, "
                            + "jsonReadable: false",
                    outcome, session.getId());
        }
    }

    private java.util.List<String> fieldNames(JsonNode node) {
        if (!node.isObject()) {
            return java.util.List.of();
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return java.util.List.copyOf(names);
    }

    private String value(JsonNode node, String fieldName, String parsedValue) {
        JsonNode value = node.path(fieldName);
        if (!value.isMissingNode() && !value.isNull()) {
            return value.isValueNode() ? value.asText() : value.getNodeType().name();
        }
        return parsedValue;
    }

    private String sanitizedPayload(JsonNode root) throws Exception {
        JsonNode copy = root.deepCopy();
        maskSensitiveFields(copy);
        String sanitized = objectMapper.writeValueAsString(copy);
        return sanitized.length() <= MAX_DIAGNOSTIC_PAYLOAD_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_DIAGNOSTIC_PAYLOAD_LENGTH) + "...[truncated]";
    }

    private void maskSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            java.util.Iterator<Map.Entry<String, JsonNode>> fields =
                    node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_FIELDS.contains(
                        field.getKey().toLowerCase(Locale.ROOT))) {
                    ((ObjectNode) node).put(field.getKey(), "***");
                } else {
                    maskSensitiveFields(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::maskSensitiveFields);
        }
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
