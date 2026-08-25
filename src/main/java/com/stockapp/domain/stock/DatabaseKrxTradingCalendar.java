package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public List<LocalDate> previousTradingDays(LocalDate date, int count) {
        Objects.requireNonNull(date, "date is required");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (count == 0) {
            return List.of();
        }

        List<LocalDate> descending = repository
                .findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        date, PageRequest.of(0, count))
                .stream()
                .map(KrxTradingDay::getTradeDate)
                .toList();
        if (descending.size() != count) {
            throw new TradingCalendarUnavailableException(
                    date, "requested previous trading days are outside synchronized coverage");
        }

        LocalDate oldest = descending.getLast();
        long expectedRows = ChronoUnit.DAYS.between(oldest, date);
        long actualRows = repository
                .countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                        oldest, date);
        if (actualRows != expectedRows) {
            throw new TradingCalendarUnavailableException(
                    date, "calendar coverage contains missing dates");
        }

        List<LocalDate> ascending = new ArrayList<>(descending);
        java.util.Collections.reverse(ascending);
        return List.copyOf(ascending);
    }
}
