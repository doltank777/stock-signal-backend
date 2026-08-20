package com.stockapp.domain.stock.dto;

import com.stockapp.domain.stock.DailyPriceFinalizationExecution;
import com.stockapp.domain.stock.DailyPriceFinalizationExecutionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record DailyPriceFinalizationExecutionSnapshot(
        Long id, LocalDate targetTradeDate,
        DailyPriceFinalizationExecutionStatus status, boolean ready,
        int attemptCount, Instant startedAt, Instant finishedAt,
        int targetStockCount, int insertedStockCount, int updatedStockCount,
        int unchangedStockCount, int noDataStockCount, int failedStockCount,
        int apiCallCount, int presentRowCount, int missingStockCount,
        String lastError
) {
    public static DailyPriceFinalizationExecutionSnapshot from(
            DailyPriceFinalizationExecution execution) {
        return new DailyPriceFinalizationExecutionSnapshot(
                execution.getId(), execution.getTargetTradeDate(),
                execution.getStatus(), execution.isReady(),
                execution.getAttemptCount(), execution.getStartedAt(),
                execution.getFinishedAt(), execution.getTargetStockCount(),
                execution.getInsertedStockCount(), execution.getUpdatedStockCount(),
                execution.getUnchangedStockCount(), execution.getNoDataStockCount(),
                execution.getFailedStockCount(), execution.getApiCallCount(),
                execution.getPresentRowCount(), execution.getMissingStockCount(),
                execution.getLastError());
    }
}
