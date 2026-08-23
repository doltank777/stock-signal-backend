package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTradingDay;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record KisTradingCalendarPage(
        int pageNumber,
        int attemptCount,
        int rowCount,
        String trCont,
        boolean contextAreaFkPresent,
        boolean contextAreaNkPresent,
        LocalDate firstDate,
        LocalDate lastDate,
        LocalDate minDate,
        LocalDate maxDate,
        KisTradingCalendarResponseOrder responseOrder,
        Set<String> outputFieldNames
) {
    public KisTradingCalendarPage {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        outputFieldNames = Set.copyOf(outputFieldNames);
    }

    public static KisTradingCalendarPage from(
            int pageNumber,
            int attemptCount,
            String trCont,
            boolean contextAreaFkPresent,
            boolean contextAreaNkPresent,
            List<KisTradingDay> rows,
            Set<String> outputFieldNames
    ) {
        LocalDate firstDate = rows.isEmpty()
                ? null : rows.getFirst().tradeDate();
        LocalDate lastDate = rows.isEmpty()
                ? null : rows.getLast().tradeDate();
        LocalDate minDate = rows.stream().map(KisTradingDay::tradeDate)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate maxDate = rows.stream().map(KisTradingDay::tradeDate)
                .max(Comparator.naturalOrder()).orElse(null);
        return new KisTradingCalendarPage(
                pageNumber, attemptCount, rows.size(), trCont,
                contextAreaFkPresent, contextAreaNkPresent,
                firstDate, lastDate, minDate, maxDate,
                KisTradingCalendarResponseOrder.from(rows), outputFieldNames);
    }
}
