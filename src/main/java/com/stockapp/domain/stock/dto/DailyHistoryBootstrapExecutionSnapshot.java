package com.stockapp.domain.stock.dto;

import com.stockapp.domain.stock.DailyHistoryBootstrapExecution;
import com.stockapp.domain.stock.DailyHistoryBootstrapExecutionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record DailyHistoryBootstrapExecutionSnapshot(
        Long id,
        LocalDate evaluationDate,
        DailyHistoryBootstrapExecutionStatus status,
        boolean ready,
        int requiredPreviousTradingDayCount,
        int requiredTradingDateCount,
        int targetStockCount,
        int completedStockCount,
        int partialStockCount,
        int failedStockCount,
        int initialMissingCount,
        int remainingMissingCount,
        int plannedRangeCount,
        int plannedChunkCount,
        int attemptedChunkCount,
        int apiCallCount,
        int savedRowCount,
        int skippedRowCount,
        int emptyResponseChunkCount,
        int outOfRangeResponseRowCount,
        Instant startedAt,
        Instant finishedAt,
        String lastError
) {
    public static DailyHistoryBootstrapExecutionSnapshot from(
            DailyHistoryBootstrapExecution execution
    ) {
        return new DailyHistoryBootstrapExecutionSnapshot(
                execution.getId(),
                execution.getEvaluationDate(),
                execution.getStatus(),
                execution.isReady(),
                execution.getRequiredPreviousTradingDayCount(),
                execution.getRequiredTradingDateCount(),
                execution.getTargetStockCount(),
                execution.getCompletedStockCount(),
                execution.getPartialStockCount(),
                execution.getFailedStockCount(),
                execution.getInitialMissingCount(),
                execution.getRemainingMissingCount(),
                execution.getPlannedRangeCount(),
                execution.getPlannedChunkCount(),
                execution.getAttemptedChunkCount(),
                execution.getApiCallCount(),
                execution.getSavedRowCount(),
                execution.getSkippedRowCount(),
                execution.getEmptyResponseChunkCount(),
                execution.getOutOfRangeResponseRowCount(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getLastError());
    }
}
