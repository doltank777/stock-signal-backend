package com.stockapp.domain.screening.admin.dto;

import java.util.List;

public record AdminRealtimeWatchStatusResponse(
        int count,
        int capacity,
        List<AdminDashboardStockResponse> stocks
) {
    public AdminRealtimeWatchStatusResponse {
        stocks = List.copyOf(stocks);
    }
}
