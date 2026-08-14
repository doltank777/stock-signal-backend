package com.stockapp.domain.stock.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LatestStockSnapshot(
        String stockCode,
        LocalDate tradeDate,
        Long currentPrice,
        Double changeRate,
        Long volume,
        LocalDateTime collectedAt
) {
}
