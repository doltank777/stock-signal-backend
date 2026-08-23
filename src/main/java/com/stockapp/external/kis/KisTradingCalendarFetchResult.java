package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTradingDay;

import java.util.List;
import java.time.LocalDate;

public record KisTradingCalendarFetchResult(
        List<KisTradingDay> days,
        List<KisTradingCalendarPage> pages,
        int apiCallCount,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        boolean requestedRangeComplete,
        boolean sourcePaginationComplete,
        boolean sourceHasMore
) {
    public KisTradingCalendarFetchResult(
            List<KisTradingDay> days,
            List<KisTradingCalendarPage> pages,
            int apiCallCount
    ) {
        this(days, pages, apiCallCount, null, null, false, true, false);
    }

    public KisTradingCalendarFetchResult {
        days = List.copyOf(days);
        pages = List.copyOf(pages);
        if (apiCallCount < pages.size()) {
            throw new IllegalArgumentException(
                    "apiCallCount must be at least page count");
        }
    }
}
