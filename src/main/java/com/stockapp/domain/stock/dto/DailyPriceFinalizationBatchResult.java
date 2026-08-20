package com.stockapp.domain.stock.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyPriceFinalizationBatchResult(
        LocalDate targetTradeDate,
        int targetStockCount,
        int insertedStockCount,
        int updatedStockCount,
        int unchangedStockCount,
        int noDataStockCount,
        int failedStockCount,
        int apiCallCount,
        boolean completed,
        Instant startedAt,
        Instant finishedAt,
        List<DailyPriceLoadFailure> failedStocks,
        List<String> noDataStockCodes,
        List<DailyPriceFinalizationTarget> targetStocks
) {
    public DailyPriceFinalizationBatchResult {
        failedStocks = List.copyOf(failedStocks);
        noDataStockCodes = List.copyOf(noDataStockCodes);
        targetStocks = List.copyOf(targetStocks);
    }

    public int successfulStockCount() {
        return insertedStockCount + updatedStockCount + unchangedStockCount;
    }
}
