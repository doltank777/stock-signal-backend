package com.stockapp.domain.stock;

public class DailyPriceFinalizationAlreadyRunningException
        extends IllegalStateException {

    public DailyPriceFinalizationAlreadyRunningException() {
        super("daily price finalization is already running in this JVM");
    }
}
