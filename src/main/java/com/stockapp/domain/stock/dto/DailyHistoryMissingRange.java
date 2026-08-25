package com.stockapp.domain.stock.dto;

import java.time.LocalDate;
import java.util.Objects;

public record DailyHistoryMissingRange(
        LocalDate startDate,
        LocalDate endDate
) {

    public DailyHistoryMissingRange {
        Objects.requireNonNull(startDate, "startDate is required");
        Objects.requireNonNull(endDate, "endDate is required");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate must be on or after startDate");
        }
    }
}
