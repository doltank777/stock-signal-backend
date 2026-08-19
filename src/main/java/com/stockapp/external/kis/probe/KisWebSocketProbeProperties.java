package com.stockapp.external.kis.probe;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "kis-websocket-probe")
public class KisWebSocketProbeProperties {

    private String stockCodes;
    private KisWebSocketProbeMode mode = KisWebSocketProbeMode.SUBSCRIBE;
    private String unsubscribeStockCode;
    private String replacementStockCode;
    private String sessionAStockCodes;
    private String sessionBStockCodes;
    private boolean logControlPayload;

    public List<String> normalizedStockCodes() {
        return normalize(stockCodes, "kis-websocket-probe.stock-codes");
    }

    public KisWebSocketProbePlan plan() {
        if (mode == null) {
            throw new IllegalArgumentException("kis-websocket-probe.mode is required");
        }
        if (mode == KisWebSocketProbeMode.MULTI_SESSION) {
            return new KisWebSocketProbePlan(
                    mode, List.of(), null, null,
                    normalize(sessionAStockCodes,
                            "kis-websocket-probe.session-a-stock-codes"),
                    normalize(sessionBStockCodes,
                            "kis-websocket-probe.session-b-stock-codes"));
        }
        List<String> initialStockCodes = normalizedStockCodes();
        if (mode == KisWebSocketProbeMode.SUBSCRIBE) {
            return new KisWebSocketProbePlan(
                    mode, initialStockCodes, null, null, List.of(), List.of());
        }

        String unsubscribe = requiredCode(
                unsubscribeStockCode,
                "kis-websocket-probe.unsubscribe-stock-code is required");
        String replacement = requiredCode(
                replacementStockCode,
                "kis-websocket-probe.replacement-stock-code is required");
        if (!initialStockCodes.contains(unsubscribe)) {
            throw new IllegalArgumentException(
                    "unsubscribe stockCode must be in the initial stockCodes");
        }
        if (unsubscribe.equals(replacement)) {
            throw new IllegalArgumentException(
                    "unsubscribe and replacement stockCodes must differ");
        }
        if (initialStockCodes.contains(replacement)) {
            throw new IllegalArgumentException(
                    "replacement stockCode must not be in the initial stockCodes");
        }
        return new KisWebSocketProbePlan(
                mode, initialStockCodes, unsubscribe, replacement,
                List.of(), List.of());
    }

    private List<String> normalize(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " is required");
        }
        List<String> parsed = Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .toList();
        if (parsed.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(propertyName
                    + " must not contain empty tokens");
        }
        return List.copyOf(new LinkedHashSet<>(parsed));
    }

    private String requiredCode(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
