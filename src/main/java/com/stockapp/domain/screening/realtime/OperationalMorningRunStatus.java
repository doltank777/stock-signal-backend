package com.stockapp.domain.screening.realtime;

public enum OperationalMorningRunStatus {
    IDLE,
    PENDING_SCREENING,
    PENDING_RECONCILIATION,
    COMPLETED,
    SKIPPED_NON_TRADING_DAY,
    FAILED_DEADLINE,
    FAILED_FATAL
}
