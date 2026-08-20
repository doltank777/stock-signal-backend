package com.stockapp.domain.screening.admin.dto;

import java.util.List;

public record AdminScreeningConditionResultResponse(
        Long searchConditionId,
        String searchConditionName,
        int priority,
        boolean realtimeEnabled,
        int stockCount,
        List<AdminDashboardStockResponse> stocks
) {
    public AdminScreeningConditionResultResponse {
        stocks = List.copyOf(stocks);
        if (stockCount != stocks.size()) {
            throw new IllegalArgumentException(
                    "stockCount must equal stocks size");
        }
    }
}
