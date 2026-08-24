package com.stockapp.domain.screening.realtime;

public enum OperationalMorningManualRetryStatus {
    EXECUTED,
    NO_PENDING_RECONCILIATION,
    OUTSIDE_MONITORING_WINDOW,
    ALREADY_RUNNING
}
