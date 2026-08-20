package com.stockapp.domain.stock;

import java.time.LocalDate;

public class TradingCalendarUnavailableException
        extends IllegalStateException {

    public TradingCalendarUnavailableException(
            LocalDate requestedDate, String reason) {
        super("KRX trading calendar unavailable for "
                + requestedDate + ": " + reason);
    }
}
