package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.dto.KisDailyPrice;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class KisDailyPriceProbeAnalyzer {

    private static final int KIS_DAILY_PRICE_RESPONSE_LIMIT = 100;
    private static final int DATE_SAMPLE_SIZE = 5;

    public KisDailyPriceProbeAnalysis analyze(
            List<KisDailyPrice> response,
            LocalDate requestedStartDate,
            LocalDate requestedEndDate
    ) {
        Objects.requireNonNull(response, "response is required");
        Objects.requireNonNull(requestedStartDate, "requestedStartDate is required");
        Objects.requireNonNull(requestedEndDate, "requestedEndDate is required");

        List<LocalDate> dates = response.stream()
                .map(KisDailyPrice::getTradeDate)
                .map(date -> Objects.requireNonNull(date, "tradeDate is required"))
                .toList();
        Set<LocalDate> distinctDates = new HashSet<>(dates);

        LocalDate earliest = dates.stream().min(LocalDate::compareTo).orElse(null);
        LocalDate latest = dates.stream().max(LocalDate::compareTo).orElse(null);
        int outOfRangeCount = (int) dates.stream()
                .filter(date -> date.isBefore(requestedStartDate)
                        || date.isAfter(requestedEndDate))
                .count();

        return new KisDailyPriceProbeAnalysis(
                dates.size(),
                earliest,
                latest,
                determineOrder(dates),
                dates.size() - distinctDates.size(),
                outOfRangeCount,
                dates.size() == KIS_DAILY_PRICE_RESPONSE_LIMIT,
                distinctDates.contains(requestedStartDate),
                distinctDates.contains(requestedEndDate),
                dates.subList(0, Math.min(DATE_SAMPLE_SIZE, dates.size())),
                dates.subList(Math.max(0, dates.size() - DATE_SAMPLE_SIZE), dates.size()));
    }

    private KisDailyPriceResponseOrder determineOrder(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return KisDailyPriceResponseOrder.EMPTY;
        }

        boolean ascending = false;
        boolean descending = false;
        LocalDate previousDistinctDate = dates.getFirst();
        for (int index = 1; index < dates.size(); index++) {
            LocalDate currentDate = dates.get(index);
            int comparison = currentDate.compareTo(previousDistinctDate);
            if (comparison > 0) {
                ascending = true;
            } else if (comparison < 0) {
                descending = true;
            }
            if (comparison != 0) {
                previousDistinctDate = currentDate;
            }
        }

        if (ascending && descending) {
            return KisDailyPriceResponseOrder.UNSORTED;
        }
        if (ascending) {
            return KisDailyPriceResponseOrder.ASCENDING;
        }
        if (descending) {
            return KisDailyPriceResponseOrder.DESCENDING;
        }
        return KisDailyPriceResponseOrder.SINGLE;
    }
}
