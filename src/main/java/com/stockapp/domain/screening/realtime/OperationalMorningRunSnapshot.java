package com.stockapp.domain.screening.realtime;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record OperationalMorningRunSnapshot(
        LocalDate date,
        OperationalMorningRunStatus status,
        int attemptCount,
        Optional<OperationalRealtimeTargetSelection> pendingSelection,
        Optional<RealtimeTargetReconciliationResult> staleClearResult
) {
    public OperationalMorningRunSnapshot {
        Objects.requireNonNull(date, "date is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(pendingSelection,
                "pendingSelection Optional is required");
        Objects.requireNonNull(staleClearResult,
                "staleClearResult Optional is required");
        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                    "attemptCount must not be negative");
        }
    }
}
