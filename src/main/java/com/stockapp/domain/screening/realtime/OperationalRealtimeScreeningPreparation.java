package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.OperationalScreeningRunResult;
import com.stockapp.domain.screening.OperationalScreeningRunStatus;

import java.util.Objects;
import java.util.Optional;

public record OperationalRealtimeScreeningPreparation(
        OperationalScreeningRunResult screeningRun,
        Optional<OperationalRealtimeTargetSelection> selection
) {

    public OperationalRealtimeScreeningPreparation {
        Objects.requireNonNull(screeningRun, "screeningRun is required");
        Objects.requireNonNull(selection, "selection Optional is required");
        boolean completed = screeningRun.status()
                == OperationalScreeningRunStatus.COMPLETED;
        if (completed != selection.isPresent()) {
            throw new IllegalArgumentException(
                    "screening preparation state is inconsistent");
        }
    }

    public static OperationalRealtimeScreeningPreparation skipped(
            OperationalScreeningRunResult screeningRun
    ) {
        return new OperationalRealtimeScreeningPreparation(
                screeningRun, Optional.empty());
    }

    public static OperationalRealtimeScreeningPreparation completed(
            OperationalScreeningRunResult screeningRun,
            OperationalRealtimeTargetSelection selection
    ) {
        return new OperationalRealtimeScreeningPreparation(
                screeningRun, Optional.of(selection));
    }
}
