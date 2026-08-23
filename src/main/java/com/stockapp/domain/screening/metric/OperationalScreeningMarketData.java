package com.stockapp.domain.screening.metric;

import com.stockapp.domain.stock.dto.DailyPriceData;

import java.util.List;
import java.util.Objects;

public record OperationalScreeningMarketData(
        OperationalCurrentMetrics current,
        List<DailyPriceData> history
) {

    public OperationalScreeningMarketData {
        Objects.requireNonNull(current, "current is required");
        history = List.copyOf(
                Objects.requireNonNull(history, "history is required"));
    }
}
