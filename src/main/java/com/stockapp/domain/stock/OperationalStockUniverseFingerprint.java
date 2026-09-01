package com.stockapp.domain.stock;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class OperationalStockUniverseFingerprint {

    public static final String HISTORY_POLICY_VERSION = "HISTORY_V1";

    public String calculate(List<Stock> stocks) {
        if (stocks == null) {
            throw new IllegalArgumentException("stocks are required");
        }
        MessageDigest digest = sha256();
        stocks.stream()
                .map(Stock::getStockCode)
                .map(this::requireStockCode)
                .sorted()
                .forEach(code -> {
                    digest.update(code.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String requireStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        return stockCode;
    }
}
