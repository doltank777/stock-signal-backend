package com.stockapp.external.kis;

import com.stockapp.domain.screening.OperationalScreeningRunResult;
import com.stockapp.domain.screening.OperationalScreeningRunStatus;
import com.stockapp.domain.screening.realtime.DesiredRealtimeTarget;
import com.stockapp.domain.screening.realtime.KrxRegularMarketSessionPolicy;
import com.stockapp.domain.screening.realtime.OperationalMorningRunCoordinator;
import com.stockapp.domain.screening.realtime.OperationalRealtimeScreeningLifecycleResult;
import com.stockapp.domain.screening.realtime.OperationalRealtimeScreeningLifecycleService;
import com.stockapp.domain.screening.realtime.OperationalRealtimeScreeningPreparation;
import com.stockapp.domain.screening.realtime.OperationalRealtimeTargetSelection;
import com.stockapp.domain.screening.realtime.RealtimeTargetReconciliationResult;
import com.stockapp.domain.screening.realtime.RealtimeTargetReconciliationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisWebSocketStartupRunnerTest {

    @Test
    void tradingDayRestartConnectsOperationalDesiredBeforeApplyingLifecycle() {
        var lifecycleService = mock(
                OperationalRealtimeScreeningLifecycleService.class);
        var sessionManager = mock(KisWebSocketSessionManager.class);
        var selection = selection("005930", "000660");
        var preparation = completedPreparation(selection);
        var result = lifecycleResult(
                RealtimeTargetReconciliationStatus.COMPLETED);
        when(lifecycleService.prepare()).thenReturn(preparation);
        when(lifecycleService.apply(preparation)).thenReturn(result);

        runner(lifecycleService, sessionManager)
                .run(mock(ApplicationArguments.class));

        var order = inOrder(lifecycleService, sessionManager);
        order.verify(lifecycleService).prepare();
        order.verify(sessionManager).connectAll(
                List.of("005930", "000660"));
        order.verify(lifecycleService).apply(preparation);
    }

    @Test
    void nonCompletedScreeningStartsNoSessionAndKeepsEmptyState() {
        var lifecycleService = mock(
                OperationalRealtimeScreeningLifecycleService.class);
        var sessionManager = mock(KisWebSocketSessionManager.class);
        var preparation = skippedPreparation(
                OperationalScreeningRunStatus.FINALIZATION_NOT_READY);
        when(lifecycleService.prepare()).thenReturn(preparation);

        runner(lifecycleService, sessionManager)
                .run(mock(ApplicationArguments.class));

        verifyNoInteractions(sessionManager);
        verify(lifecycleService, never()).apply(preparation);
    }

    @Test
    void emptyDesiredAppliesWithoutOpeningEmptySession() {
        var lifecycleService = mock(
                OperationalRealtimeScreeningLifecycleService.class);
        var sessionManager = mock(KisWebSocketSessionManager.class);
        var selection = selection();
        var preparation = completedPreparation(selection);
        var result = lifecycleResult(
                RealtimeTargetReconciliationStatus.NO_OP);
        when(lifecycleService.prepare()).thenReturn(preparation);
        when(lifecycleService.apply(preparation)).thenReturn(result);

        runner(lifecycleService, sessionManager)
                .run(mock(ApplicationArguments.class));

        verifyNoInteractions(sessionManager);
        verify(lifecycleService).apply(preparation);
    }

    @Test
    void reconciliationPartialFailureDoesNotFailApplicationStartup() {
        var lifecycleService = mock(
                OperationalRealtimeScreeningLifecycleService.class);
        var sessionManager = mock(KisWebSocketSessionManager.class);
        var selection = selection("005930");
        var preparation = completedPreparation(selection);
        var result = lifecycleResult(
                RealtimeTargetReconciliationStatus.PARTIAL_FAILURE);
        when(lifecycleService.prepare()).thenReturn(preparation);
        when(lifecycleService.apply(preparation)).thenReturn(result);

        runner(lifecycleService, sessionManager)
                .run(mock(ApplicationArguments.class));

        verify(sessionManager).connectAll(List.of("005930"));
        verify(lifecycleService).apply(preparation);
    }

    @Test
    void initialConnectionFailureStopsBeforeRegistryReconciliation() {
        var lifecycleService = mock(
                OperationalRealtimeScreeningLifecycleService.class);
        var sessionManager = mock(KisWebSocketSessionManager.class);
        var preparation = completedPreparation(selection("005930"));
        var failure = new KisWebSocketException(
                "connection failed", (Throwable) null);
        when(lifecycleService.prepare()).thenReturn(preparation);
        org.mockito.Mockito.doThrow(failure).when(sessionManager)
                .connectAll(List.of("005930"));

        assertThatThrownBy(() -> runner(lifecycleService, sessionManager)
                .run(mock(ApplicationArguments.class))).isSameAs(failure);
        verify(lifecycleService, never()).apply(preparation);
    }

    @Test
    void calendarOrInvariantFailureRemainsFailClosed() {
        var lifecycleService = mock(
                OperationalRealtimeScreeningLifecycleService.class);
        var sessionManager = mock(KisWebSocketSessionManager.class);
        var failure = new IllegalStateException("operational failure");
        when(lifecycleService.prepare()).thenThrow(failure);

        assertThatThrownBy(() -> runner(lifecycleService, sessionManager)
                .run(mock(ApplicationArguments.class))).isSameAs(failure);
        verifyNoInteractions(sessionManager);
    }

    @Test
    void outsideRecoveryWindowSkipsBeforeOperationalPreparation() {
        var lifecycleService = mock(
                OperationalRealtimeScreeningLifecycleService.class);
        var sessionManager = mock(KisWebSocketSessionManager.class);
        var policy = mock(KrxRegularMarketSessionPolicy.class);
        var coordinator = mock(OperationalMorningRunCoordinator.class);
        when(policy.isStartupRecoveryWindow()).thenReturn(false);

        new KisWebSocketStartupRunner(lifecycleService, sessionManager,
                policy, coordinator).run(mock(ApplicationArguments.class));

        verifyNoInteractions(lifecycleService, sessionManager, coordinator);
    }

    private KisWebSocketStartupRunner runner(
            OperationalRealtimeScreeningLifecycleService lifecycleService,
            KisWebSocketSessionManager sessionManager
    ) {
        KrxRegularMarketSessionPolicy policy =
                mock(KrxRegularMarketSessionPolicy.class);
        when(policy.isStartupRecoveryWindow()).thenReturn(true);
        return new KisWebSocketStartupRunner(lifecycleService, sessionManager,
                policy, mock(OperationalMorningRunCoordinator.class));
    }

    private OperationalRealtimeScreeningPreparation completedPreparation(
            OperationalRealtimeTargetSelection selection
    ) {
        OperationalScreeningRunResult screeningRun =
                mock(OperationalScreeningRunResult.class);
        when(screeningRun.status()).thenReturn(
                OperationalScreeningRunStatus.COMPLETED);
        return OperationalRealtimeScreeningPreparation.completed(
                screeningRun, selection);
    }

    private OperationalRealtimeScreeningPreparation skippedPreparation(
            OperationalScreeningRunStatus status
    ) {
        OperationalScreeningRunResult screeningRun =
                mock(OperationalScreeningRunResult.class);
        when(screeningRun.status()).thenReturn(status);
        return OperationalRealtimeScreeningPreparation.skipped(screeningRun);
    }

    private OperationalRealtimeTargetSelection selection(String... codes) {
        OperationalRealtimeTargetSelection selection =
                mock(OperationalRealtimeTargetSelection.class);
        List<DesiredRealtimeTarget> targets = java.util.Arrays.stream(codes)
                .map(code -> {
                    DesiredRealtimeTarget target =
                            mock(DesiredRealtimeTarget.class);
                    when(target.stockCode()).thenReturn(code);
                    return target;
                }).toList();
        when(selection.selectedTargets()).thenReturn(targets);
        org.mockito.Mockito.lenient().when(selection.selectedCount())
                .thenReturn(targets.size());
        return selection;
    }

    private OperationalRealtimeScreeningLifecycleResult lifecycleResult(
            RealtimeTargetReconciliationStatus status
    ) {
        RealtimeTargetReconciliationResult reconciliation =
                mock(RealtimeTargetReconciliationResult.class);
        when(reconciliation.status()).thenReturn(status);
        org.mockito.Mockito.lenient().when(reconciliation.afterPhysicalCount())
                .thenReturn(status == RealtimeTargetReconciliationStatus
                        .PARTIAL_FAILURE ? 0 : 1);
        OperationalRealtimeScreeningLifecycleResult result =
                mock(OperationalRealtimeScreeningLifecycleResult.class);
        when(result.reconciliation()).thenReturn(Optional.of(reconciliation));
        return result;
    }
}
