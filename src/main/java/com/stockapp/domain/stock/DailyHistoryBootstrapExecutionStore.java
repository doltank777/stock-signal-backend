package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import com.stockapp.domain.stock.dto.DailyHistoryBootstrapExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyHistoryBootstrapExecutionStore {

    private static final int MAX_ERROR_LENGTH = 1000;
    private final DailyHistoryBootstrapExecutionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyHistoryBootstrapExecutionSnapshot start(
            LocalDate evaluationDate,
            int requiredPreviousTradingDayCount,
            Instant now
    ) {
        DailyHistoryBootstrapExecution execution = repository.saveAndFlush(
                DailyHistoryBootstrapExecution.create(
                        evaluationDate, requiredPreviousTradingDayCount, now));
        return DailyHistoryBootstrapExecutionSnapshot.from(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyHistoryBootstrapExecutionSnapshot complete(
            Long executionId,
            BootstrapDailyHistoryBatchResult result,
            Instant now
    ) {
        DailyHistoryBootstrapExecution execution = require(executionId);
        execution.complete(result, now);
        return DailyHistoryBootstrapExecutionSnapshot.from(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyHistoryBootstrapExecutionSnapshot fail(
            Long executionId,
            Throwable error,
            Instant now
    ) {
        DailyHistoryBootstrapExecution execution = require(executionId);
        execution.fail(errorSummary(error), now);
        return DailyHistoryBootstrapExecutionSnapshot.from(execution);
    }

    @Transactional(readOnly = true)
    public Optional<DailyHistoryBootstrapExecutionSnapshot> findLatest(
            LocalDate evaluationDate
    ) {
        return repository
                .findFirstByEvaluationDateOrderByFinishedAtDescIdDesc(
                        evaluationDate)
                .map(DailyHistoryBootstrapExecutionSnapshot::from);
    }

    @Transactional(readOnly = true)
    public Optional<DailyHistoryBootstrapExecutionSnapshot> findLatestReady(
            LocalDate evaluationDate,
            int requiredPreviousTradingDayCount
    ) {
        return repository
                .findFirstByEvaluationDateAndReadyTrueAndRequiredPreviousTradingDayCountGreaterThanEqualOrderByFinishedAtDescIdDesc(
                        evaluationDate, requiredPreviousTradingDayCount)
                .map(DailyHistoryBootstrapExecutionSnapshot::from);
    }

    private DailyHistoryBootstrapExecution require(Long executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "bootstrap execution not found: " + executionId));
    }

    private String errorSummary(Throwable error) {
        String message = error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage());
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }
}
