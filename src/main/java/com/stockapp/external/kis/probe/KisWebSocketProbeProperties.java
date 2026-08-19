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

    public List<String> normalizedStockCodes() {
        if (stockCodes == null || stockCodes.isBlank()) {
            throw new IllegalArgumentException(
                    "kis-websocket-probe.stock-codes is required");
        }
        List<String> parsed = Arrays.stream(stockCodes.split(",", -1))
                .map(String::trim)
                .toList();
        if (parsed.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "kis-websocket-probe.stock-codes must not contain empty tokens");
        }
        return List.copyOf(new LinkedHashSet<>(parsed));
    }
}
