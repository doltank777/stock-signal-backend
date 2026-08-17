package com.stockapp.domain.screening.metric;

public record ScreeningDataRequirements(
        boolean snapshotRequired,
        int maxDailyPeriod
) {

    public ScreeningDataRequirements {
        if (maxDailyPeriod < 0) {
            throw new IllegalArgumentException(
                    "maxDailyPeriod must not be negative");
        }
    }
}
