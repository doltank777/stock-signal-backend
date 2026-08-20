package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionSnapshot;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationRecoveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyPriceFinalizationRecoveryService {

    private final DailyPriceFinalizationBatchService batchService;
    private final DailyPriceFinalizationExecutionStore executionStore;
    private final DailyPriceFinalizationRunGuard runGuard;
    private final Clock clock;

    public DailyPriceFinalizationRecoveryResult finalizeAll(
            LocalDate targetTradeDate) {
        return execute(targetTradeDate, false);
    }

    public DailyPriceFinalizationRecoveryResult recover(
            LocalDate targetTradeDate) {
        return execute(targetTradeDate, true);
    }

    public List<DailyPriceFinalizationExecutionSnapshot>
    findIncompleteExecutions() {
        return executionStore.findIncomplete();
    }

    public Optional<DailyPriceFinalizationExecutionSnapshot>
    findLatestExecution() {
        return executionStore.findLatest();
    }

    private DailyPriceFinalizationRecoveryResult execute(
            LocalDate targetTradeDate, boolean skipAlreadyReady) {
        if (targetTradeDate == null) {
            throw new IllegalArgumentException("targetTradeDate is required");
        }
        LocalDate today = LocalDate.now(clock.withZone(
                ZoneId.of("Asia/Seoul")));
        if (targetTradeDate.isAfter(today)) {
            throw new IllegalArgumentException(
                    "targetTradeDate must not be in the future");
        }
        runGuard.acquire();
        try {
            if (skipAlreadyReady) {
                var existing = executionStore.find(targetTradeDate);
                if (existing.isPresent()
                        && existing.get().status()
                        == DailyPriceFinalizationExecutionStatus.COMPLETED
                        && existing.get().ready()) {
                    return new DailyPriceFinalizationRecoveryResult(
                            true, existing.get());
                }
            }
            Instant startedAt = Instant.now(clock);
            DailyPriceFinalizationExecutionSnapshot running =
                    executionStore.start(targetTradeDate, startedAt);
            try {
                DailyPriceFinalizationExecutionResult result =
                        batchService.finalizeAllWithinGuard(targetTradeDate);
                var completed = executionStore.complete(
                        running.id(), result, Instant.now(clock));
                return new DailyPriceFinalizationRecoveryResult(false, completed);
            } catch (DailyPriceFinalizationInterruptedException exception) {
                executionStore.fail(running.id(),
                        DailyPriceFinalizationExecutionStatus.INTERRUPTED,
                        exception, Instant.now(clock));
                throw exception;
            } catch (RuntimeException exception) {
                executionStore.fail(running.id(),
                        DailyPriceFinalizationExecutionStatus.FAILED,
                        exception, Instant.now(clock));
                throw exception;
            }
        } finally {
            runGuard.release();
        }
    }
}
