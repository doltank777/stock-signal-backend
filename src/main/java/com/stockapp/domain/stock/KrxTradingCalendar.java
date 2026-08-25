package com.stockapp.domain.stock;

import java.time.LocalDate;
import java.util.List;

public interface KrxTradingCalendar {

    boolean isTradingDay(LocalDate date);

    LocalDate previousTradingDay(LocalDate date);

    List<LocalDate> previousTradingDays(LocalDate date, int count);

    List<LocalDate> tradingDaysBetween(
            LocalDate startDate,
            LocalDate endDate);
}
