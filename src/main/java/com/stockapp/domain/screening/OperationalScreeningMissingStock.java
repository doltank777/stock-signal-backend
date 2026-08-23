package com.stockapp.domain.screening;

import com.stockapp.domain.stock.MarketType;

import java.util.Objects;

public record OperationalScreeningMissingStock(
        Long stockId,
        String stockCode,
        String stockName,
        MarketType marketType
) {

    public OperationalScreeningMissingStock {
        Objects.requireNonNull(stockId, "stockId is required");
        Objects.requireNonNull(stockCode, "stockCode is required");
        Objects.requireNonNull(stockName, "stockName is required");
        Objects.requireNonNull(marketType, "marketType is required");
    }
}
