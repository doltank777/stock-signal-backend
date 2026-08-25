package com.stockapp.domain.stock.dto;

import java.time.LocalDate;
import java.util.Objects;

public record BootstrapDailyHistoryRequest(
        LocalDate evaluationDate,
        int requiredPreviousTradingDayCount
) {
    public BootstrapDailyHistoryRequest {
        Objects.requireNonNull(evaluationDate, "evaluationDate is required");
        if (requiredPreviousTradingDayCount < 0) {
            throw new IllegalArgumentException(
                    "requiredPreviousTradingDayCount must not be negative");
        }
    }
}
