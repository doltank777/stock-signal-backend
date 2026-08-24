package com.stockapp.domain.screening.realtime;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

public record OperationalMorningRunSnapshot(
        LocalDate date,
        OperationalMorningRunStatus status,
        int attemptCount,
        Optional<ZonedDateTime> lastAttemptAt,
        Optional<OperationalRealtimeTargetSelection> pendingSelection,
        Optional<RealtimeTargetReconciliationResult> lastReconciliation,
        Optional<RealtimeTargetReconciliationResult> staleClearResult
) {
    public OperationalMorningRunSnapshot {
        Objects.requireNonNull(date, "date is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(lastAttemptAt,
                "lastAttemptAt Optional is required");
        Objects.requireNonNull(pendingSelection,
                "pendingSelection Optional is required");
        Objects.requireNonNull(staleClearResult,
                "staleClearResult Optional is required");
        Objects.requireNonNull(lastReconciliation,
                "lastReconciliation Optional is required");
        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                    "attemptCount must not be negative");
        }
    }
}
