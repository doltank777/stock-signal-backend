package com.stockapp.domain.stock;

import java.time.LocalDate;

public interface KrxTradingCalendar {

    boolean isTradingDay(LocalDate date);

    LocalDate previousTradingDay(LocalDate date);
}
