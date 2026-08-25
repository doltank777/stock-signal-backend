package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyHistoryMissingRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
public class DailyHistoryMissingRangePlanner {

    private final KrxTradingCalendar tradingCalendar;

    public List<DailyHistoryMissingRange> plan(
            List<LocalDate> missingTradingDates
    ) {
        Objects.requireNonNull(missingTradingDates,
                "missingTradingDates is required");
        missingTradingDates.forEach(date -> Objects.requireNonNull(date,
                "missingTradingDates must not contain null"));

        List<LocalDate> missingDates = new TreeSet<>(
                missingTradingDates).stream().toList();
        if (missingDates.isEmpty()) {
            return List.of();
        }

        List<LocalDate> tradingDates = tradingCalendar.tradingDaysBetween(
                missingDates.getFirst(), missingDates.getLast());
        Set<LocalDate> missingSet = new HashSet<>(missingDates);
        if (!new HashSet<>(tradingDates).containsAll(missingSet)) {
            throw new IllegalArgumentException(
                    "missingTradingDates must contain only KRX trading days");
        }

        List<DailyHistoryMissingRange> ranges = new ArrayList<>();
        LocalDate rangeStart = null;
        LocalDate rangeEnd = null;
        for (LocalDate tradingDate : tradingDates) {
            if (missingSet.contains(tradingDate)) {
                if (rangeStart == null) {
                    rangeStart = tradingDate;
                }
                rangeEnd = tradingDate;
            } else if (rangeStart != null) {
                ranges.add(new DailyHistoryMissingRange(
                        rangeStart, rangeEnd));
                rangeStart = null;
                rangeEnd = null;
            }
        }
        if (rangeStart != null) {
            ranges.add(new DailyHistoryMissingRange(rangeStart, rangeEnd));
        }
        return List.copyOf(ranges);
    }
}
