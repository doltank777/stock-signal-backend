package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.OperationalScreeningRunStatus;
import com.stockapp.domain.stock.TradingCalendarUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class OperationalMorningRunCoordinator {

    private final OperationalRealtimeScreeningLifecycleService lifecycleService;
    private final RealtimeTargetReconciliationService reconciliationService;
    private final KrxRegularMarketSessionPolicy sessionPolicy;
    private final AtomicBoolean running = new AtomicBoolean();
    private MorningState state;

    public OperationalMorningRunCoordinator(
            OperationalRealtimeScreeningLifecycleService lifecycleService,
            RealtimeTargetReconciliationService reconciliationService,
            KrxRegularMarketSessionPolicy sessionPolicy
    ) {
        this.lifecycleService = lifecycleService;
        this.reconciliationService = reconciliationService;
        this.sessionPolicy = sessionPolicy;
    }

    public void executeTick() {
        ZonedDateTime now = sessionPolicy.now();
        LocalDate today = now.toLocalDate();
        resetFor(today);
        if (isTerminal() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!sessionPolicy.isMorningPreparationWindow(now)) {
                if (sessionPolicy.isMorningDeadlineReached(now)) {
                    failClosed(today, "post-deadline tick");
                }
                return;
            }
            if (!sessionPolicy.isMorningDeadlineReached(now)
                    && !sessionPolicy.isRetryDue(now, previousAttempt())) {
                return;
            }
            incrementAttempt(now);
            try {
                if (pendingSelection().isPresent()) {
                    handleReconciliation(today,
                            reconciliationService.reconcile(
                                    pendingSelection().orElseThrow()),
                            pendingSelection().orElseThrow());
                } else {
                    handleLifecycle(today, lifecycleService.run());
                }
            } catch (TradingCalendarUnavailableException exception) {
                markPendingScreening();
                log.warn("operational morning calendar unavailable - date: {}, "
                        + "attempt: {}", today, attemptCount(), exception);
            } catch (IllegalArgumentException exception) {
                markFatal();
                log.error("operational morning invariant failure - date: {}",
                        today, exception);
                return;
            } catch (RuntimeException exception) {
                markFatal();
                log.error("operational morning fatal failure - date: {}",
                        today, exception);
                return;
            }
            if (!isTerminal()
                    && sessionPolicy.isMorningDeadlineReached(now)) {
                failClosed(today, "morning deadline reached");
            }
        } finally {
            running.set(false);
        }
    }

    public void recordStartupResult(
            OperationalRealtimeScreeningLifecycleResult result
    ) {
        LocalDate today = sessionPolicy.today();
        resetFor(today);
        if (result.reconciliation().isEmpty()) {
            return;
        }
        RealtimeTargetReconciliationResult reconciliation =
                result.reconciliation().orElseThrow();
        if (reconciliation.status()
                == RealtimeTargetReconciliationStatus.PARTIAL_FAILURE) {
            setState(OperationalMorningRunStatus.PENDING_RECONCILIATION,
                    result.selection().orElseThrow(), null, reconciliation);
        } else {
            setState(OperationalMorningRunStatus.COMPLETED, null, null,
                    reconciliation);
        }
    }

    public synchronized OperationalMorningRunSnapshot snapshot() {
        LocalDate today = sessionPolicy.today();
        resetFor(today);
        return state.snapshot();
    }

    public OperationalMorningManualRetryResult retryPendingReconciliationNow() {
        ZonedDateTime now = sessionPolicy.now();
        LocalDate today = now.toLocalDate();
        resetFor(today);
        if (!sessionPolicy.isStartupRecoveryWindow()) {
            return OperationalMorningManualRetryResult.outsideWindow();
        }
        if (!running.compareAndSet(false, true)) {
            return OperationalMorningManualRetryResult.alreadyRunning();
        }
        try {
            OperationalRealtimeTargetSelection selection = pendingSelection()
                    .orElse(null);
            if (selection == null) {
                return OperationalMorningManualRetryResult.noPending();
            }
            incrementAttempt(now);
            RealtimeTargetReconciliationResult reconciliation =
                    reconciliationService.reconcile(selection);
            handleReconciliation(today, reconciliation, selection);
            return OperationalMorningManualRetryResult.executed(
                    reconciliation);
        } finally {
            running.set(false);
        }
    }

    private void handleLifecycle(
            LocalDate today,
            OperationalRealtimeScreeningLifecycleResult result
    ) {
        OperationalScreeningRunStatus screeningStatus =
                result.screeningRun().status();
        if (screeningStatus == OperationalScreeningRunStatus.NOT_TRADING_DAY) {
            RealtimeTargetReconciliationResult clear = clearStaleTargets();
            setState(OperationalMorningRunStatus.SKIPPED_NON_TRADING_DAY,
                    null, clear, clear);
            return;
        }
        if (screeningStatus != OperationalScreeningRunStatus.COMPLETED) {
            markPendingScreening();
            return;
        }
        handleReconciliation(today, result.reconciliation().orElseThrow(),
                result.selection().orElseThrow());
    }

    private void handleReconciliation(
            LocalDate today,
            RealtimeTargetReconciliationResult result,
            OperationalRealtimeTargetSelection selection
    ) {
        if (result.status()
                == RealtimeTargetReconciliationStatus.PARTIAL_FAILURE) {
            setState(OperationalMorningRunStatus.PENDING_RECONCILIATION,
                    selection, null, result);
            log.warn("operational morning reconciliation pending - date: {}, "
                            + "attempt: {}, operation: {}, stockCode: {}, "
                            + "beforePhysical: {}, afterPhysical: {}",
                    today, attemptCount(), result.failedOperation(),
                    result.failedStockCode(), result.beforePhysicalCount(),
                    result.afterPhysicalCount());
            return;
        }
        setState(OperationalMorningRunStatus.COMPLETED, null, null, result);
        log.info("operational morning completed - date: {}, selectedCount: {}, "
                        + "excludedCount: {}, reconciliationStatus: {}, attempt: {}",
                today, selection.selectedCount(), selection.excludedCount(),
                result.status(), attemptCount());
    }

    private void failClosed(LocalDate today, String reason) {
        RealtimeTargetReconciliationResult clear = clearStaleTargets();
        setState(OperationalMorningRunStatus.FAILED_DEADLINE, null, clear,
                clear);
        log.warn("operational morning failed closed - date: {}, reason: {}, "
                        + "attempts: {}, clearStatus: {}, remainingPhysical: {}",
                today, reason, attemptCount(), clear.status(),
                clear.afterPhysicalCount());
    }

    private RealtimeTargetReconciliationResult clearStaleTargets() {
        return reconciliationService.reconcile(
                OperationalRealtimeTargetSelection.empty());
    }

    private synchronized void resetFor(LocalDate today) {
        if (state == null || !state.date.equals(today)) {
            state = new MorningState(today,
                    OperationalMorningRunStatus.IDLE, 0, null, null, null,
                    null);
        }
    }

    private synchronized void incrementAttempt(ZonedDateTime attemptedAt) {
        state = new MorningState(state.date, state.status,
                state.attemptCount + 1, state.pendingSelection,
                state.staleClearResult, state.lastReconciliation, attemptedAt);
    }

    private synchronized ZonedDateTime previousAttempt() {
        return state.previousAttempt;
    }

    private synchronized int attemptCount() {
        return state.attemptCount;
    }

    private synchronized Optional<OperationalRealtimeTargetSelection>
    pendingSelection() {
        return Optional.ofNullable(state.pendingSelection);
    }

    private synchronized boolean isTerminal() {
        return switch (state.status) {
            case COMPLETED, SKIPPED_NON_TRADING_DAY,
                    FAILED_DEADLINE, FAILED_FATAL -> true;
            default -> false;
        };
    }

    private synchronized void markPendingScreening() {
        setState(OperationalMorningRunStatus.PENDING_SCREENING, null, null,
                null);
    }

    private synchronized void markFatal() {
        setState(OperationalMorningRunStatus.FAILED_FATAL, null, null, null);
    }

    private synchronized void setState(
            OperationalMorningRunStatus status,
            OperationalRealtimeTargetSelection pendingSelection,
            RealtimeTargetReconciliationResult clearResult,
            RealtimeTargetReconciliationResult lastReconciliation
    ) {
        state = new MorningState(state.date, status, state.attemptCount,
                pendingSelection, clearResult, lastReconciliation,
                state.previousAttempt);
    }

    private record MorningState(
            LocalDate date,
            OperationalMorningRunStatus status,
            int attemptCount,
            OperationalRealtimeTargetSelection pendingSelection,
            RealtimeTargetReconciliationResult staleClearResult,
            RealtimeTargetReconciliationResult lastReconciliation,
            ZonedDateTime previousAttempt
    ) {
        private OperationalMorningRunSnapshot snapshot() {
            return new OperationalMorningRunSnapshot(
                    date, status, attemptCount,
                    Optional.ofNullable(previousAttempt),
                    Optional.ofNullable(pendingSelection),
                    Optional.ofNullable(lastReconciliation),
                    Optional.ofNullable(staleClearResult));
        }
    }
}
