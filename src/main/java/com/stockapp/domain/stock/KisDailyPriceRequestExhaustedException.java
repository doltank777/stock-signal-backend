package com.stockapp.domain.stock;

public class KisDailyPriceRequestExhaustedException extends RuntimeException {
    private final int attemptCount;

    public KisDailyPriceRequestExhaustedException(RuntimeException cause, int attemptCount) {
        super(cause);
        this.attemptCount = attemptCount;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
