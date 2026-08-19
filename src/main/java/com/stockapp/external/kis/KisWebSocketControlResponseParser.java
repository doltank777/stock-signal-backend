package com.stockapp.external.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class KisWebSocketControlResponseParser {

    private final ObjectMapper objectMapper;

    public Optional<KisWebSocketControlResponse> parse(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode header = root.path("header");
            JsonNode body = root.path("body");
            String trId = text(header, "tr_id");
            String trKey = text(header, "tr_key");
            String returnCode = text(body, "rt_cd");
            if (trId == null || trKey == null || returnCode == null) {
                return Optional.empty();
            }
            return Optional.of(new KisWebSocketControlResponse(
                    trId,
                    trKey,
                    returnCode,
                    text(body, "msg_cd"),
                    text(body, "msg1")));
        } catch (RuntimeException | java.io.IOException exception) {
            return Optional.empty();
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() ? value.textValue() : null;
    }
}
