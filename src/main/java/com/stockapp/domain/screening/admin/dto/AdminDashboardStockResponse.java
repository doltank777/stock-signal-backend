package com.stockapp.domain.screening.admin.dto;

import com.stockapp.domain.stock.MarketType;

public record AdminDashboardStockResponse(
        Long stockId,
        String stockCode,
        String stockName,
        MarketType market
) {
}
