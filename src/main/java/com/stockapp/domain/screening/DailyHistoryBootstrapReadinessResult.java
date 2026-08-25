package com.stockapp.domain.screening;

import com.stockapp.domain.stock.dto.DailyHistoryBootstrapExecutionSnapshot;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record DailyHistoryBootstrapReadinessResult(
        LocalDate evaluationDate,
        int requiredPreviousTradingDayCount,
        Optional<DailyHistoryBootstrapExecutionSnapshot> matchedExecution
) {
    public DailyHistoryBootstrapReadinessResult {
        Objects.requireNonNull(evaluationDate, "evaluationDate is required");
        Objects.requireNonNull(matchedExecution,
                "matchedExecution Optional is required");
        if (requiredPreviousTradingDayCount < 0) {
            throw new IllegalArgumentException(
                    "requiredPreviousTradingDayCount must not be negative");
        }
    }

    public boolean ready() {
        return matchedExecution.isPresent();
    }
}
