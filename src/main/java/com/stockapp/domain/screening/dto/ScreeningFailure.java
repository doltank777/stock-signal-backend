package com.stockapp.domain.screening.dto;

public record ScreeningFailure(
        String stockCode,
        String stockName,
        String reason,
        String message
) {

    public ScreeningFailure {
        validateRequired(stockCode, "stockCode");
        validateRequired(stockName, "stockName");
        validateRequired(reason, "reason");
        validateRequired(message, "message");
    }

    private static void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
