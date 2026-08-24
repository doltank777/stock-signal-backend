package com.stockapp.domain.screening.realtime;

import java.util.Objects;
import java.util.Optional;

public record OperationalMorningManualRetryResult(
        OperationalMorningManualRetryStatus status,
        Optional<RealtimeTargetReconciliationResult> reconciliation
) {
    public OperationalMorningManualRetryResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(reconciliation, "reconciliation Optional is required");
    }

    public static OperationalMorningManualRetryResult executed(
            RealtimeTargetReconciliationResult result) {
        return new OperationalMorningManualRetryResult(
                OperationalMorningManualRetryStatus.EXECUTED,
                Optional.of(Objects.requireNonNull(result)));
    }

    public static OperationalMorningManualRetryResult noPending() {
        return empty(OperationalMorningManualRetryStatus.NO_PENDING_RECONCILIATION);
    }

    public static OperationalMorningManualRetryResult outsideWindow() {
        return empty(OperationalMorningManualRetryStatus.OUTSIDE_MONITORING_WINDOW);
    }

    public static OperationalMorningManualRetryResult alreadyRunning() {
        return empty(OperationalMorningManualRetryStatus.ALREADY_RUNNING);
    }

    private static OperationalMorningManualRetryResult empty(
            OperationalMorningManualRetryStatus status) {
        return new OperationalMorningManualRetryResult(status, Optional.empty());
    }
}
