package com.stockapp.domain.screening.metric;

public record OperationalScreeningDataRequirements(
        int maxHistoryPeriod,
        boolean changeRateRequired
) {

    public OperationalScreeningDataRequirements {
        if (maxHistoryPeriod < 0) {
            throw new IllegalArgumentException(
                    "maxHistoryPeriod must not be negative");
        }
    }

    public int requiredPreviousRowCount() {
        return Math.max(maxHistoryPeriod, changeRateRequired ? 1 : 0);
    }
}
