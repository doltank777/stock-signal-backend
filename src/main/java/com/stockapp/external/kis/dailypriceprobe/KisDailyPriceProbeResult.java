package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.dto.KisDailyPrice;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record KisDailyPriceProbeResult(
        String stockCode,
        LocalDate targetDate,
        OffsetDateTime requestedAt,
        int responseRowCount,
        KisDailyPrice row
) {
    public boolean rowFound() {
        return row != null;
    }
}
