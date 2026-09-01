package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_history_bootstrap_executions", indexes = @Index(
        name = "idx_daily_history_bootstrap_readiness",
        columnList = "evaluation_date, ready, required_previous_trading_day_count"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyHistoryBootstrapExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_date", nullable = false)
    private LocalDate evaluationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyHistoryBootstrapExecutionStatus status;

    @Column(nullable = false)
    private boolean ready;

    @Column(name = "required_previous_trading_day_count", nullable = false)
    private int requiredPreviousTradingDayCount;
    @Column(name = "target_universe_fingerprint", length = 64)
    private String targetUniverseFingerprint;
    @Column(name = "target_universe_policy_version", length = 30)
    private String targetUniversePolicyVersion;
    @Column(name = "required_trading_date_count", nullable = false)
    private int requiredTradingDateCount;
    @Column(name = "target_stock_count", nullable = false)
    private int targetStockCount;
    @Column(name = "completed_stock_count", nullable = false)
    private int completedStockCount;
    @Column(name = "partial_stock_count", nullable = false)
    private int partialStockCount;
    @Column(name = "failed_stock_count", nullable = false)
    private int failedStockCount;
    @Column(name = "initial_missing_count", nullable = false)
    private int initialMissingCount;
    @Column(name = "remaining_missing_count", nullable = false)
    private int remainingMissingCount;
    @Column(name = "planned_range_count", nullable = false)
    private int plannedRangeCount;
    @Column(name = "planned_chunk_count", nullable = false)
    private int plannedChunkCount;
    @Column(name = "attempted_chunk_count", nullable = false)
    private int attemptedChunkCount;
    @Column(name = "api_call_count", nullable = false)
    private int apiCallCount;
    @Column(name = "saved_row_count", nullable = false)
    private int savedRowCount;
    @Column(name = "skipped_row_count", nullable = false)
    private int skippedRowCount;
    @Column(name = "empty_response_chunk_count", nullable = false)
    private int emptyResponseChunkCount;
    @Column(name = "out_of_range_response_row_count", nullable = false)
    private int outOfRangeResponseRowCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "last_error", length = 1000)
    private String lastError;

    public static DailyHistoryBootstrapExecution create(
            LocalDate evaluationDate,
            int requiredPreviousTradingDayCount,
            String targetUniverseFingerprint,
            String targetUniversePolicyVersion,
            Instant now
    ) {
        if (requiredPreviousTradingDayCount < 0) {
            throw new IllegalArgumentException(
                    "requiredPreviousTradingDayCount must not be negative");
        }
        DailyHistoryBootstrapExecution execution =
                new DailyHistoryBootstrapExecution();
        execution.evaluationDate = evaluationDate;
        execution.requiredPreviousTradingDayCount =
                requiredPreviousTradingDayCount;
        execution.targetUniverseFingerprint = requireMetadata(
                targetUniverseFingerprint, "targetUniverseFingerprint");
        execution.targetUniversePolicyVersion = requireMetadata(
                targetUniversePolicyVersion, "targetUniversePolicyVersion");
        execution.status = DailyHistoryBootstrapExecutionStatus.RUNNING;
        execution.startedAt = now;
        return execution;
    }

    private static String requireMetadata(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    public void complete(BootstrapDailyHistoryBatchResult result, Instant now) {
        if (!evaluationDate.equals(result.evaluationDate())
                || requiredPreviousTradingDayCount
                != result.requiredPreviousTradingDayCount()) {
            throw new IllegalArgumentException(
                    "batch result does not match bootstrap execution contract");
        }
        status = switch (result.status()) {
            case COMPLETED -> DailyHistoryBootstrapExecutionStatus.COMPLETED;
            case COMPLETED_WITH_GAPS ->
                    DailyHistoryBootstrapExecutionStatus.COMPLETED_WITH_GAPS;
        };
        ready = result.ready();
        requiredTradingDateCount = result.requiredTradingDateCount();
        targetStockCount = result.targetStockCount();
        completedStockCount = result.completedStockCount();
        partialStockCount = result.partialStockCount();
        failedStockCount = result.failedStockCount();
        initialMissingCount = result.totalInitialMissingCount();
        remainingMissingCount = result.totalRemainingMissingCount();
        plannedRangeCount = result.plannedRangeCount();
        plannedChunkCount = result.plannedChunkCount();
        attemptedChunkCount = result.attemptedChunkCount();
        apiCallCount = result.apiCallCount();
        savedRowCount = result.savedRowCount();
        skippedRowCount = result.skippedRowCount();
        emptyResponseChunkCount = result.emptyResponseChunkCount();
        outOfRangeResponseRowCount = result.outOfRangeResponseRowCount();
        finishedAt = now;
        lastError = null;
    }

    public void fail(String error, Instant now) {
        status = DailyHistoryBootstrapExecutionStatus.FAILED;
        ready = false;
        finishedAt = now;
        lastError = error;
    }
}
