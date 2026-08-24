package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.OperationalScreeningRunResult;
import com.stockapp.domain.screening.OperationalScreeningRunStatus;

import java.util.Objects;
import java.util.Optional;

public record OperationalRealtimeScreeningLifecycleResult(
        OperationalScreeningRunResult screeningRun,
        Optional<OperationalRealtimeTargetSelection> selection,
        Optional<RealtimeTargetReconciliationResult> reconciliation
) {

    public OperationalRealtimeScreeningLifecycleResult {
        Objects.requireNonNull(screeningRun, "screeningRun is required");
        Objects.requireNonNull(selection, "selection Optional is required");
        Objects.requireNonNull(reconciliation,
                "reconciliation Optional is required");
        boolean completed = screeningRun.status()
                == OperationalScreeningRunStatus.COMPLETED;
        if (completed != selection.isPresent()
                || completed != reconciliation.isPresent()) {
            throw new IllegalArgumentException(
                    "lifecycle result state is inconsistent");
        }
    }

    public static OperationalRealtimeScreeningLifecycleResult skipped(
            OperationalScreeningRunResult screeningRun
    ) {
        return new OperationalRealtimeScreeningLifecycleResult(
                screeningRun, Optional.empty(), Optional.empty());
    }

    public static OperationalRealtimeScreeningLifecycleResult completed(
            OperationalScreeningRunResult screeningRun,
            OperationalRealtimeTargetSelection selection,
            RealtimeTargetReconciliationResult reconciliation
    ) {
        return new OperationalRealtimeScreeningLifecycleResult(
                screeningRun, Optional.of(selection),
                Optional.of(reconciliation));
    }
}
