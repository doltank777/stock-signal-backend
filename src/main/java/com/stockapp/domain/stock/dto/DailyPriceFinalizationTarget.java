package com.stockapp.domain.stock.dto;

public record DailyPriceFinalizationTarget(
        Long stockId,
        String stockCode,
        String stockName
) {
}
