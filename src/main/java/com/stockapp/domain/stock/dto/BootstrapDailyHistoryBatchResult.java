package com.stockapp.domain.stock.dto;

import com.stockapp.domain.stock.BootstrapDailyHistoryBatchStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record BootstrapDailyHistoryBatchResult(
        BootstrapDailyHistoryBatchStatus status,
        LocalDate evaluationDate,
        int requiredPreviousTradingDayCount,
        int requiredTradingDateCount,
        int targetStockCount,
        int completedStockCount,
        int partialStockCount,
        int failedStockCount,
        int totalInitialMissingCount,
        int totalRemainingMissingCount,
        int plannedRangeCount,
        int plannedChunkCount,
        int attemptedChunkCount,
        int apiCallCount,
        int savedRowCount,
        int skippedRowCount,
        int emptyResponseChunkCount,
        int outOfRangeResponseRowCount,
        List<BootstrapDailyHistoryStockSummary> problemStocks
) {

    public BootstrapDailyHistoryBatchResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(evaluationDate, "evaluationDate is required");
        Objects.requireNonNull(problemStocks, "problemStocks is required");
        problemStocks = List.copyOf(problemStocks);
    }

    public boolean ready() {
        return totalRemainingMissingCount == 0 && failedStockCount == 0;
    }
}
