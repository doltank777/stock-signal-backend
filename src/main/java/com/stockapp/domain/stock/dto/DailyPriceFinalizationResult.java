package com.stockapp.domain.stock.dto;

import com.stockapp.domain.stock.DailyPriceFinalizationStatus;

import java.time.LocalDate;

public record DailyPriceFinalizationResult(
        String stockCode,
        LocalDate targetTradeDate,
        DailyPriceFinalizationStatus status
) {
}
