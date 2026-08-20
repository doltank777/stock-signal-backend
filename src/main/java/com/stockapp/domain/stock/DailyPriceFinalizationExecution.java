package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_price_finalization_executions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_price_finalization_execution_trade_date",
                columnNames = "target_trade_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPriceFinalizationExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_trade_date", nullable = false)
    private LocalDate targetTradeDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailyPriceFinalizationExecutionStatus status;

    @Column(nullable = false)
    private boolean ready;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "target_stock_count", nullable = false)
    private int targetStockCount;
    @Column(name = "inserted_stock_count", nullable = false)
    private int insertedStockCount;
    @Column(name = "updated_stock_count", nullable = false)
    private int updatedStockCount;
    @Column(name = "unchanged_stock_count", nullable = false)
    private int unchangedStockCount;
    @Column(name = "no_data_stock_count", nullable = false)
    private int noDataStockCount;
    @Column(name = "failed_stock_count", nullable = false)
    private int failedStockCount;
    @Column(name = "api_call_count", nullable = false)
    private int apiCallCount;
    @Column(name = "present_row_count", nullable = false)
    private int presentRowCount;
    @Column(name = "missing_stock_count", nullable = false)
    private int missingStockCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DailyPriceFinalizationExecution create(
            LocalDate targetTradeDate, Instant now) {
        DailyPriceFinalizationExecution execution =
                new DailyPriceFinalizationExecution();
        execution.targetTradeDate = targetTradeDate;
        execution.createdAt = now;
        execution.start(now);
        return execution;
    }

    public void start(Instant now) {
        status = DailyPriceFinalizationExecutionStatus.RUNNING;
        ready = false;
        attemptCount++;
        startedAt = now;
        finishedAt = null;
        targetStockCount = 0;
        insertedStockCount = 0;
        updatedStockCount = 0;
        unchangedStockCount = 0;
        noDataStockCount = 0;
        failedStockCount = 0;
        apiCallCount = 0;
        presentRowCount = 0;
        missingStockCount = 0;
        lastError = null;
        updatedAt = now;
    }

    public void complete(DailyPriceFinalizationExecutionResult result,
                         Instant now) {
        var batch = result.batch();
        var completeness = result.completeness();
        if (!targetTradeDate.equals(batch.targetTradeDate())
                || !targetTradeDate.equals(completeness.targetTradeDate())
                || !batch.completed()) {
            throw new IllegalArgumentException(
                    "execution result does not represent a completed target date");
        }
        if (completeness.ready() && (batch.failedStockCount() != 0
                || batch.noDataStockCount() != 0
                || completeness.missingStockCount() != 0)) {
            throw new IllegalArgumentException(
                    "ready execution cannot contain failed, no-data, or missing stocks");
        }
        status = DailyPriceFinalizationExecutionStatus.COMPLETED;
        ready = completeness.ready();
        finishedAt = now;
        targetStockCount = batch.targetStockCount();
        insertedStockCount = batch.insertedStockCount();
        updatedStockCount = batch.updatedStockCount();
        unchangedStockCount = batch.unchangedStockCount();
        noDataStockCount = batch.noDataStockCount();
        failedStockCount = batch.failedStockCount();
        apiCallCount = batch.apiCallCount();
        presentRowCount = completeness.presentRowCount();
        missingStockCount = completeness.missingStockCount();
        lastError = null;
        updatedAt = now;
    }

    public void fail(DailyPriceFinalizationExecutionStatus failureStatus,
                     String error, Instant now) {
        if (failureStatus != DailyPriceFinalizationExecutionStatus.FAILED
                && failureStatus
                != DailyPriceFinalizationExecutionStatus.INTERRUPTED) {
            throw new IllegalArgumentException("invalid failure status");
        }
        status = failureStatus;
        ready = false;
        finishedAt = now;
        lastError = error;
        updatedAt = now;
    }
}
