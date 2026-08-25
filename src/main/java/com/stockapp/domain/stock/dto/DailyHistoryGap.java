package com.stockapp.domain.stock.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DailyHistoryGap(
        List<LocalDate> missingTradingDates
) {

    public DailyHistoryGap {
        Objects.requireNonNull(missingTradingDates,
                "missingTradingDates is required");
        missingTradingDates = List.copyOf(missingTradingDates);
    }

    public boolean complete() {
        return missingTradingDates.isEmpty();
    }
}
