package com.stockapp.external.kis.dailypriceprobe;

import java.time.LocalDate;
import java.util.List;

public record KisDailyPriceProbeAnalysis(
        int responseRowCount,
        LocalDate earliestResponseDate,
        LocalDate latestResponseDate,
        KisDailyPriceResponseOrder responseOrder,
        int duplicateDateCount,
        int outOfRangeDateCount,
        boolean limitReached,
        boolean exactStartDatePresent,
        boolean exactEndDatePresent,
        List<LocalDate> firstResponseDates,
        List<LocalDate> lastResponseDates
) {

    public KisDailyPriceProbeAnalysis {
        firstResponseDates = List.copyOf(firstResponseDates);
        lastResponseDates = List.copyOf(lastResponseDates);
    }
}
