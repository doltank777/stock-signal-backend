package com.stockapp.domain.screening.metric;

public record OperationalDailyHistoryRequirement(
        int screeningMaxHistoryPeriod,
        int screeningRequiredPreviousTradingDayCount,
        int signalMaxHistoryPeriod,
        int signalRequiredPreviousTradingDayCount,
        int requiredPreviousTradingDayCount,
        boolean evaluationDateRowRequired
) {

    public OperationalDailyHistoryRequirement {
        if (screeningMaxHistoryPeriod < 0
                || screeningRequiredPreviousTradingDayCount < 0
                || signalMaxHistoryPeriod < 0
                || signalRequiredPreviousTradingDayCount < 0
                || requiredPreviousTradingDayCount < 0) {
            throw new IllegalArgumentException(
                    "daily history requirements must not be negative");
        }
        if (screeningRequiredPreviousTradingDayCount
                < screeningMaxHistoryPeriod) {
            throw new IllegalArgumentException(
                    "screening previous-day count must cover its max period");
        }
        if (signalRequiredPreviousTradingDayCount < signalMaxHistoryPeriod) {
            throw new IllegalArgumentException(
                    "signal previous-day count must cover its max period");
        }
        int expectedRequiredPreviousTradingDayCount = Math.max(
                screeningRequiredPreviousTradingDayCount,
                signalRequiredPreviousTradingDayCount);
        if (requiredPreviousTradingDayCount
                != expectedRequiredPreviousTradingDayCount) {
            throw new IllegalArgumentException(
                    "required previous-day count must be the pipeline maximum");
        }
    }

    public int requiredRowCountIncludingEvaluationDate() {
        return Math.addExact(requiredPreviousTradingDayCount,
                evaluationDateRowRequired ? 1 : 0);
    }
}
