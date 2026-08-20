package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatabaseKrxTradingCalendar implements KrxTradingCalendar {

    private final KrxTradingDayRepository repository;

    @Override
    public boolean isTradingDay(LocalDate date) {
        Objects.requireNonNull(date, "date is required");
        return repository.findById(date)
                .orElseThrow(() -> new TradingCalendarUnavailableException(
                        date, "date is outside synchronized coverage"))
                .isTradingDay();
    }

    @Override
    public LocalDate previousTradingDay(LocalDate date) {
        Objects.requireNonNull(date, "date is required");
        LocalDate previous = repository
                .findFirstByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        date)
                .map(KrxTradingDay::getTradeDate)
                .orElseThrow(() -> new TradingCalendarUnavailableException(
                        date, "previous trading day is outside synchronized coverage"));
        long expectedRows = ChronoUnit.DAYS.between(previous, date);
        long actualRows = repository
                .countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                        previous, date);
        if (actualRows != expectedRows) {
            throw new TradingCalendarUnavailableException(
                    date, "calendar coverage contains missing dates");
        }
        return previous;
    }
}
