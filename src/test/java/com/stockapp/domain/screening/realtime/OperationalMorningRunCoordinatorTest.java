package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.OperationalScreeningRunResult;
import com.stockapp.domain.screening.OperationalScreeningRunStatus;
import com.stockapp.domain.stock.TradingCalendarUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationalMorningRunCoordinatorTest {

    private final OperationalRealtimeScreeningLifecycleService lifecycle =
            mock(OperationalRealtimeScreeningLifecycleService.class);
    private final RealtimeTargetReconciliationService reconciliation =
            mock(RealtimeTargetReconciliationService.class);
    private final KrxRegularMarketSessionPolicy policy =
            mock(KrxRegularMarketSessionPolicy.class);
    private final AtomicReference<ZonedDateTime> now =
            new AtomicReference<>();
    private OperationalMorningRunCoordinator coordinator;

    @BeforeEach
    void setUp() {
        when(policy.now()).thenAnswer(ignored -> now.get());
        when(policy.today()).thenAnswer(ignored -> now.get().toLocalDate());
        when(policy.isMorningPreparationWindow(
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            int minute = ((ZonedDateTime) invocation.getArgument(0))
                    .getMinute();
            int hour = ((ZonedDateTime) invocation.getArgument(0)).getHour();
            return hour == 8 && minute >= 30 && minute <= 55;
        });
        when(policy.isMorningDeadlineReached(
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ZonedDateTime value = invocation.getArgument(0);
            return value.getHour() > 8
                    || value.getHour() == 8 && value.getMinute() >= 55;
        });
        when(policy.isRetryDue(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.nullable(ZonedDateTime.class)))
                .thenReturn(true);
        coordinator = new OperationalMorningRunCoordinator(
                lifecycle, reconciliation, policy);
    }

    @Test
    void successfulMorningRunsOncePerKoreaDateAndResetsNextDay() {
        at(2026, 8, 24, 8, 30);
        OperationalRealtimeScreeningLifecycleResult completed = completed(
                selection(), result(
                        RealtimeTargetReconciliationStatus.COMPLETED));
        when(lifecycle.run()).thenReturn(completed);

        coordinator.executeTick();
        coordinator.executeTick();
        assertThat(coordinator.snapshot().status())
                .isEqualTo(OperationalMorningRunStatus.COMPLETED);
        verify(lifecycle, times(1)).run();

        at(2026, 8, 25, 8, 30);
        coordinator.executeTick();
        verify(lifecycle, times(2)).run();
    }

    @Test
    void partialFailureRetriesOnlySameSelection() {
        at(2026, 8, 24, 8, 30);
        OperationalRealtimeTargetSelection selection = selection();
        OperationalRealtimeScreeningLifecycleResult partial = completed(
                selection, result(
                        RealtimeTargetReconciliationStatus.PARTIAL_FAILURE));
        RealtimeTargetReconciliationResult completed = result(
                RealtimeTargetReconciliationStatus.COMPLETED);
        when(lifecycle.run()).thenReturn(partial);
        when(reconciliation.reconcile(selection)).thenReturn(completed);

        coordinator.executeTick();
        at(2026, 8, 24, 8, 35);
        coordinator.executeTick();

        verify(lifecycle, times(1)).run();
        verify(reconciliation).reconcile(selection);
        assertThat(coordinator.snapshot().status())
                .isEqualTo(OperationalMorningRunStatus.COMPLETED);
        assertThat(coordinator.snapshot().pendingSelection()).isEmpty();
    }

    @Test
    void manualRetryReusesPendingSelectionAndCompletesMorningState() {
        at(2026, 8, 24, 8, 30);
        OperationalRealtimeTargetSelection selection = selection();
        RealtimeTargetReconciliationResult partial = result(
                RealtimeTargetReconciliationStatus.PARTIAL_FAILURE);
        OperationalRealtimeScreeningLifecycleResult lifecycleResult =
                completed(selection, partial);
        when(lifecycle.run()).thenReturn(lifecycleResult);
        coordinator.executeTick();
        when(policy.isStartupRecoveryWindow()).thenReturn(true);
        RealtimeTargetReconciliationResult completed = result(
                RealtimeTargetReconciliationStatus.COMPLETED);
        when(reconciliation.reconcile(selection)).thenReturn(completed);

        OperationalMorningManualRetryResult result =
                coordinator.retryPendingReconciliationNow();

        assertThat(result.status()).isEqualTo(
                OperationalMorningManualRetryStatus.EXECUTED);
        assertThat(coordinator.snapshot().status()).isEqualTo(
                OperationalMorningRunStatus.COMPLETED);
        verify(lifecycle).run();
        verify(reconciliation).reconcile(selection);
    }

    @Test
    void manualRetryWithoutPendingSelectionIsExplicitNoOp() {
        at(2026, 8, 24, 10, 0);
        when(policy.isStartupRecoveryWindow()).thenReturn(true);

        assertThat(coordinator.retryPendingReconciliationNow().status())
                .isEqualTo(OperationalMorningManualRetryStatus
                        .NO_PENDING_RECONCILIATION);
        verifyNoInteractions(lifecycle, reconciliation);
    }

    @Test
    void deadlineMakesLastAttemptThenClearsStaleTargets() {
        at(2026, 8, 24, 8, 55);
        OperationalRealtimeScreeningLifecycleResult notReady = skipped(
                OperationalScreeningRunStatus.FINALIZATION_NOT_READY);
        when(lifecycle.run()).thenReturn(notReady);
        var clear = result(RealtimeTargetReconciliationStatus.COMPLETED);
        when(reconciliation.reconcile(
                org.mockito.ArgumentMatchers.argThat(selection ->
                        selection.selectedTargets().isEmpty())))
                .thenReturn(clear);

        coordinator.executeTick();

        verify(lifecycle).run();
        assertThat(coordinator.snapshot().status())
                .isEqualTo(OperationalMorningRunStatus.FAILED_DEADLINE);
        assertThat(coordinator.snapshot().staleClearResult()).contains(clear);
    }

    @Test
    void nonTradingDayClearsPhysicalTargetsAndStopsRetry() {
        at(2026, 8, 24, 8, 30);
        OperationalRealtimeScreeningLifecycleResult nonTrading = skipped(
                OperationalScreeningRunStatus.NOT_TRADING_DAY);
        RealtimeTargetReconciliationResult clear = result(
                RealtimeTargetReconciliationStatus.NO_OP);
        when(lifecycle.run()).thenReturn(nonTrading);
        when(reconciliation.reconcile(
                org.mockito.ArgumentMatchers.any())).thenReturn(clear);

        coordinator.executeTick();
        coordinator.executeTick();

        assertThat(coordinator.snapshot().status()).isEqualTo(
                OperationalMorningRunStatus.SKIPPED_NON_TRADING_DAY);
        verify(lifecycle, times(1)).run();
        verify(reconciliation, times(1)).reconcile(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void postDeadlineTickNeverStartsScreeningAndClearsOnly() {
        at(2026, 8, 24, 9, 10);
        RealtimeTargetReconciliationResult clear = result(
                RealtimeTargetReconciliationStatus.COMPLETED);
        when(reconciliation.reconcile(
                org.mockito.ArgumentMatchers.any())).thenReturn(clear);

        coordinator.executeTick();

        verify(lifecycle, never()).run();
        assertThat(coordinator.snapshot().status())
                .isEqualTo(OperationalMorningRunStatus.FAILED_DEADLINE);
    }

    @Test
    void calendarFailureCanRetryOnNextTick() {
        at(2026, 8, 24, 8, 30);
        OperationalRealtimeScreeningLifecycleResult completed = completed(
                selection(), result(
                        RealtimeTargetReconciliationStatus.COMPLETED));
        when(lifecycle.run()).thenThrow(
                new TradingCalendarUnavailableException(
                        LocalDate.of(2026, 8, 24), "missing"))
                .thenReturn(completed);

        coordinator.executeTick();
        at(2026, 8, 24, 8, 35);
        coordinator.executeTick();

        verify(lifecycle, times(2)).run();
        assertThat(coordinator.snapshot().status())
                .isEqualTo(OperationalMorningRunStatus.COMPLETED);
    }

    @Test
    void overlappingTicksAreRejectedAndGuardIsReleased() throws Exception {
        at(2026, 8, 24, 8, 30);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OperationalRealtimeScreeningLifecycleResult completed = completed(
                selection(), result(
                        RealtimeTargetReconciliationStatus.COMPLETED));
        when(lifecycle.run()).thenAnswer(ignored -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return completed;
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(coordinator::executeTick);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            coordinator.executeTick();
            verify(lifecycle, times(1)).run();
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        }
        assertThat(coordinator.snapshot().status())
                .isEqualTo(OperationalMorningRunStatus.COMPLETED);
    }

    private void at(int year, int month, int day, int hour, int minute) {
        now.set(ZonedDateTime.of(year, month, day, hour, minute, 0, 0,
                ZoneId.of("Asia/Seoul")));
    }

    private OperationalRealtimeScreeningLifecycleResult completed(
            OperationalRealtimeTargetSelection selection,
            RealtimeTargetReconciliationResult reconciliationResult
    ) {
        OperationalScreeningRunResult run = run(
                OperationalScreeningRunStatus.COMPLETED);
        return OperationalRealtimeScreeningLifecycleResult.completed(
                run, selection, reconciliationResult);
    }

    private OperationalRealtimeScreeningLifecycleResult skipped(
            OperationalScreeningRunStatus status
    ) {
        return OperationalRealtimeScreeningLifecycleResult.skipped(run(status));
    }

    private OperationalScreeningRunResult run(
            OperationalScreeningRunStatus status
    ) {
        OperationalScreeningRunResult run =
                mock(OperationalScreeningRunResult.class);
        when(run.status()).thenReturn(status);
        return run;
    }

    private OperationalRealtimeTargetSelection selection() {
        OperationalRealtimeTargetSelection selection =
                mock(OperationalRealtimeTargetSelection.class);
        when(selection.selectedCount()).thenReturn(1);
        when(selection.excludedCount()).thenReturn(0);
        return selection;
    }

    private RealtimeTargetReconciliationResult result(
            RealtimeTargetReconciliationStatus status
    ) {
        RealtimeTargetReconciliationResult result =
                mock(RealtimeTargetReconciliationResult.class);
        when(result.status()).thenReturn(status);
        org.mockito.Mockito.lenient().when(result.beforePhysicalCount())
                .thenReturn(1);
        org.mockito.Mockito.lenient().when(result.afterPhysicalCount())
                .thenReturn(status == RealtimeTargetReconciliationStatus
                        .PARTIAL_FAILURE ? 1 : 0);
        return result;
    }
}
