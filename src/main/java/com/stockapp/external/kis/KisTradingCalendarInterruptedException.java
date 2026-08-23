package com.stockapp.external.kis;

public class KisTradingCalendarInterruptedException extends RuntimeException {

    public KisTradingCalendarInterruptedException(InterruptedException cause) {
        super("KIS Trading Calendar request pacing was interrupted", cause);
    }
}
