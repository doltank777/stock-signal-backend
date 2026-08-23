package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTradingDay;

import java.time.LocalDate;
import java.util.List;

public enum KisTradingCalendarResponseOrder {
    ASCENDING,
    DESCENDING,
    MIXED,
    SINGLE,
    EMPTY;

    public static KisTradingCalendarResponseOrder from(
            List<KisTradingDay> rows
    ) {
        if (rows.isEmpty()) {
            return EMPTY;
        }
        if (rows.size() == 1) {
            return SINGLE;
        }
        boolean ascending = true;
        boolean descending = true;
        boolean increased = false;
        boolean decreased = false;
        for (int index = 1; index < rows.size(); index++) {
            LocalDate previous = rows.get(index - 1).tradeDate();
            LocalDate current = rows.get(index).tradeDate();
            if (current.isBefore(previous)) {
                ascending = false;
                decreased = true;
            } else if (current.isAfter(previous)) {
                descending = false;
                increased = true;
            }
        }
        if (ascending && increased) {
            return ASCENDING;
        }
        if (descending && decreased) {
            return DESCENDING;
        }
        return MIXED;
    }
}
