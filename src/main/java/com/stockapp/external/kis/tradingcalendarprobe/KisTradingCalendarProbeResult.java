package com.stockapp.external.kis.tradingcalendarprobe;

import com.stockapp.external.kis.KisTradingCalendarPage;
import com.stockapp.external.kis.KisTradingCalendarResponseOrder;
import com.stockapp.external.kis.dto.KisTradingDay;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public record KisTradingCalendarProbeResult(
        LocalDate baseDate,
        LocalDate requestedEndDate,
        OffsetDateTime requestedAt,
        boolean paginationComplete,
        boolean requestedRangeComplete,
        boolean sourceHasMore,
        List<KisTradingDay> rows,
        List<KisTradingCalendarPage> pages,
        LocalDate firstCollectedDate,
        LocalDate lastCollectedDate,
        LocalDate minCollectedDate,
        LocalDate maxCollectedDate,
        KisTradingCalendarResponseOrder overallResponseOrder,
        long uniqueDateCount,
        long duplicateDateCount,
        long expectedCalendarDateCount,
        boolean baseDatePresent,
        long tradingDayCount,
        long closedDayCount,
        long weekendRowCount,
        long saturdayRowCount,
        long sundayRowCount,
        boolean containsSaturday,
        boolean containsSunday,
        long futureDateCount,
        long pastDateCount,
        long missingCalendarDateCount,
        int apiCallCount,
        Set<String> outputFieldNames
) {
    public KisTradingCalendarProbeResult {
        rows = List.copyOf(rows);
        pages = List.copyOf(pages);
        outputFieldNames = Set.copyOf(outputFieldNames);
    }

    public String mode() {
        return requestedEndDate == null ? "DIAGNOSTIC" : "RANGE";
    }

    public int rowCount() {
        return rows.size();
    }

    public int pageCount() {
        return pages.size();
    }

    public LocalDate firstDate() {
        return minCollectedDate;
    }

    public LocalDate lastDate() {
        return maxCollectedDate;
    }
}
