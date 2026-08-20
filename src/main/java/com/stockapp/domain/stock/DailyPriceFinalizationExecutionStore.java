package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionSnapshot;
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
public class DailyPriceFinalizationExecutionStore {

    private static final int MAX_ERROR_LENGTH = 1000;
    private final DailyPriceFinalizationExecutionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyPriceFinalizationExecutionSnapshot start(
            LocalDate targetTradeDate, Instant now) {
        DailyPriceFinalizationExecution execution = repository
                .findByTargetTradeDate(targetTradeDate)
                .orElseGet(() -> DailyPriceFinalizationExecution.create(
                        targetTradeDate, now));
        if (execution.getId() != null) {
            execution.start(now);
        }
        return DailyPriceFinalizationExecutionSnapshot.from(
                repository.saveAndFlush(execution));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyPriceFinalizationExecutionSnapshot complete(
            Long executionId, DailyPriceFinalizationExecutionResult result,
            Instant now) {
        DailyPriceFinalizationExecution execution = require(executionId);
        execution.complete(result, now);
        return DailyPriceFinalizationExecutionSnapshot.from(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyPriceFinalizationExecutionSnapshot fail(
            Long executionId,
            DailyPriceFinalizationExecutionStatus status,
            Throwable error,
            Instant now) {
        DailyPriceFinalizationExecution execution = require(executionId);
        execution.fail(status, errorSummary(error), now);
        return DailyPriceFinalizationExecutionSnapshot.from(execution);
    }

    @Transactional(readOnly = true)
    public Optional<DailyPriceFinalizationExecutionSnapshot> find(
            LocalDate targetTradeDate) {
        return repository.findByTargetTradeDate(targetTradeDate)
                .map(DailyPriceFinalizationExecutionSnapshot::from);
    }

    @Transactional(readOnly = true)
    public List<DailyPriceFinalizationExecutionSnapshot> findRunning() {
        return repository.findByStatusOrderByStartedAtAsc(
                        DailyPriceFinalizationExecutionStatus.RUNNING).stream()
                .map(DailyPriceFinalizationExecutionSnapshot::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyPriceFinalizationExecutionSnapshot> findIncomplete() {
        return repository.findByReadyFalseOrderByStartedAtAsc().stream()
                .map(DailyPriceFinalizationExecutionSnapshot::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DailyPriceFinalizationExecutionSnapshot> findLatest() {
        return repository.findFirstByOrderByStartedAtDesc()
                .map(DailyPriceFinalizationExecutionSnapshot::from);
    }

    private DailyPriceFinalizationExecution require(Long executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "finalization execution not found: " + executionId));
    }

    private String errorSummary(Throwable error) {
        String message = error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage());
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }
}
