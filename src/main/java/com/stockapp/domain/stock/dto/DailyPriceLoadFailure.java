package com.stockapp.domain.stock.dto;

public record DailyPriceLoadFailure(
        String stockCode,
        String stockName,
        String reason,
        String messageCode) {
}
