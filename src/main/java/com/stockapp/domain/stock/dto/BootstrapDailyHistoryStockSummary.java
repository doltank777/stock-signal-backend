package com.stockapp.domain.stock.dto;

import com.stockapp.domain.stock.BootstrapMissingHistoryFetchFillStatus;

import java.util.Objects;

public record BootstrapDailyHistoryStockSummary(
        String stockCode,
        BootstrapMissingHistoryFetchFillStatus status,
        int remainingMissingCount,
        BootstrapMissingHistoryFetchFillFailure failure
) {

    public BootstrapDailyHistoryStockSummary {
        Objects.requireNonNull(stockCode, "stockCode is required");
        Objects.requireNonNull(status, "status is required");
        if (remainingMissingCount < 0) {
            throw new IllegalArgumentException(
                    "remainingMissingCount must not be negative");
        }
    }
}
