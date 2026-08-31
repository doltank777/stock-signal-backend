package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.dto.KisDailyPrice;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record KisDailyPriceProbeResult(
        String stockCode,
        LocalDate targetDate,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        OffsetDateTime requestedAt,
        int httpStatus,
        String returnCode,
        String messageCode,
        String message,
        KisDailyPriceProbeClassification classification,
        KisDailyPrice row,
        KisDailyPriceProbeAnalysis analysis
) {
    public boolean rowFound() {
        return row != null;
    }

    public int responseRowCount() {
        return analysis.responseRowCount();
    }
}
