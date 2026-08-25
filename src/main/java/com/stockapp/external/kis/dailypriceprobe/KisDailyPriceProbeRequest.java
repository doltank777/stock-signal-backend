package com.stockapp.external.kis.dailypriceprobe;

import java.time.LocalDate;
import java.util.Objects;

public record KisDailyPriceProbeRequest(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate targetDate
) {

    public KisDailyPriceProbeRequest {
        Objects.requireNonNull(startDate, "startDate is required");
        Objects.requireNonNull(endDate, "endDate is required");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must be on or after startDate");
        }
    }

    public boolean singleDateMode() {
        return targetDate != null;
    }
}
