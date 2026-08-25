package com.stockapp.domain.stock;

public class KisDailyPriceRequestInterruptedException extends RuntimeException {
    public KisDailyPriceRequestInterruptedException(InterruptedException cause) {
        super(cause);
    }
}
