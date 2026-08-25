package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.LatestScreeningSnapshotRegistry;
import com.stockapp.domain.screening.OperationalScreeningRunResult;
import com.stockapp.domain.screening.OperationalScreeningRunService;
import com.stockapp.domain.screening.OperationalScreeningRunStatus;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.TradingCalendarUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalRealtimeScreeningLifecycleServiceTest {

    @Mock OperationalScreeningRunService screeningRunService;
    @Mock LatestScreeningSnapshotRegistry screeningSnapshotRegistry;
    @Mock OperationalRealtimeTargetSelector targetSelector;
    @Mock LatestOperationalRealtimeSelectionRegistry selectionRegistry;
    @Mock RealtimeTargetReconciliationService reconciliationService;
    @Mock ScreeningRunResult screeningResult;

    @ParameterizedTest
    @EnumSource(value = OperationalScreeningRunStatus.class,
            names = {"NOT_TRADING_DAY", "FINALIZATION_NOT_READY",
                    "HISTORY_BOOTSTRAP_NOT_READY", "DATA_INCOMPLETE"})
    void nonCompletedScreeningSkipsEveryRealtimeSideEffect(
            OperationalScreeningRunStatus status
    ) {
        OperationalScreeningRunResult screeningRun = screeningRun(status);
        when(screeningRunService.run()).thenReturn(screeningRun);

        var result = service().run();

        assertThat(result.screeningRun()).isSameAs(screeningRun);
        assertThat(result.selection()).isEmpty();
        assertThat(result.reconciliation()).isEmpty();
        verifyNoInteractions(screeningSnapshotRegistry, targetSelector,
                selectionRegistry, reconciliationService);
    }

    @Test
    void completedPublishesThenSelectsAndReconcilesExactResults() {
        OperationalScreeningRunResult screeningRun = completedRun();
        OperationalRealtimeTargetSelection selection = selection();
        RealtimeTargetReconciliationResult reconciliation = reconciliation(
                RealtimeTargetReconciliationStatus.COMPLETED);
        when(screeningRunService.run()).thenReturn(screeningRun);
        when(targetSelector.select(screeningResult)).thenReturn(selection);
        when(reconciliationService.reconcile(selection))
                .thenReturn(reconciliation);

        var result = service().run();

        assertThat(result.screeningRun()).isSameAs(screeningRun);
        assertThat(result.selection()).containsSame(selection);
        assertThat(result.reconciliation()).containsSame(reconciliation);
        InOrder order = inOrder(screeningSnapshotRegistry, targetSelector,
                selectionRegistry, reconciliationService);
        order.verify(screeningSnapshotRegistry).replace(screeningResult);
        order.verify(targetSelector).select(screeningResult);
        order.verify(selectionRegistry).replace(selection);
        order.verify(reconciliationService).reconcile(selection);
    }

    @Test
    void emptySelectionStillReconciles() {
        OperationalScreeningRunResult screeningRun = completedRun();
        OperationalRealtimeTargetSelection emptySelection =
                new OperationalRealtimeTargetSelection(
                        RealtimeWatchPolicy.CAPACITY, 0,
                        List.of(), List.of());
        var reconciliation = reconciliation(
                RealtimeTargetReconciliationStatus.COMPLETED);
        when(screeningRunService.run()).thenReturn(screeningRun);
        when(targetSelector.select(screeningResult))
                .thenReturn(emptySelection);
        when(reconciliationService.reconcile(emptySelection))
                .thenReturn(reconciliation);

        var result = service().run();

        assertThat(result.selection().orElseThrow().selectedTargets())
                .isEmpty();
        verify(reconciliationService).reconcile(emptySelection);
    }

    @Test
    void noOpReconciliationIsPreservedAsSuccessfulNestedResult() {
        var result = runCompletedWith(
                RealtimeTargetReconciliationStatus.NO_OP);

        assertThat(result.reconciliation().orElseThrow().status())
                .isEqualTo(RealtimeTargetReconciliationStatus.NO_OP);
    }

    @Test
    void partialFailureDiagnosticsAndSelectionArePreserved() {
        OperationalScreeningRunResult screeningRun = completedRun();
        OperationalRealtimeTargetSelection selection = selection();
        var partial = new RealtimeTargetReconciliationResult(
                RealtimeTargetReconciliationStatus.PARTIAL_FAILURE,
                1, 1, 0, List.of("A"), List.of(),
                List.of("B"), List.of(), 0,
                com.stockapp.external.kis.RealtimeSubscriptionCommandOperation
                        .SUBSCRIBE,
                "B", "KisWebSocketException", "failed", List.of());
        when(screeningRunService.run()).thenReturn(screeningRun);
        when(targetSelector.select(screeningResult)).thenReturn(selection);
        when(reconciliationService.reconcile(selection)).thenReturn(partial);

        var result = service().run();

        assertThat(result.selection()).containsSame(selection);
        assertThat(result.reconciliation()).containsSame(partial);
        assertThat(result.reconciliation().orElseThrow().failedStockCode())
                .isEqualTo("B");
        verify(screeningSnapshotRegistry).replace(screeningResult);
    }

    @Test
    void calendarUnavailablePropagatesWithoutDownstreamCalls() {
        var failure = new TradingCalendarUnavailableException(
                LocalDate.of(2026, 8, 24), "missing");
        when(screeningRunService.run()).thenThrow(failure);

        assertThatThrownBy(() -> service().run()).isSameAs(failure);
        verifyNoInteractions(screeningSnapshotRegistry, targetSelector,
                reconciliationService);
    }

    @Test
    void selectorInvariantFailurePropagatesAfterSnapshotPublication() {
        var failure = new IllegalArgumentException("invalid selection");
        OperationalScreeningRunResult screeningRun = completedRun();
        when(screeningRunService.run()).thenReturn(screeningRun);
        when(targetSelector.select(screeningResult)).thenThrow(failure);

        assertThatThrownBy(() -> service().run()).isSameAs(failure);
        verify(screeningSnapshotRegistry).replace(screeningResult);
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void snapshotPublicationFailureStopsBeforeRealtimeChanges() {
        var failure = new IllegalStateException("snapshot failure");
        OperationalScreeningRunResult screeningRun = completedRun();
        when(screeningRunService.run()).thenReturn(screeningRun);
        doThrow(failure).when(screeningSnapshotRegistry)
                .replace(screeningResult);

        assertThatThrownBy(() -> service().run()).isSameAs(failure);
        verifyNoInteractions(targetSelector, reconciliationService);
    }

    @Test
    void reconciliationInvariantFailurePropagatesWithoutSnapshotRollback() {
        OperationalRealtimeTargetSelection selection = selection();
        var failure = new IllegalStateException("reconciliation invariant");
        OperationalScreeningRunResult screeningRun = completedRun();
        when(screeningRunService.run()).thenReturn(screeningRun);
        when(targetSelector.select(screeningResult)).thenReturn(selection);
        when(reconciliationService.reconcile(selection)).thenThrow(failure);

        assertThatThrownBy(() -> service().run()).isSameAs(failure);
        verify(screeningSnapshotRegistry).replace(screeningResult);
        verify(selectionRegistry).replace(selection);
    }

    private OperationalRealtimeScreeningLifecycleResult runCompletedWith(
            RealtimeTargetReconciliationStatus status
    ) {
        OperationalRealtimeTargetSelection selection = selection();
        RealtimeTargetReconciliationResult reconciliation =
                reconciliation(status);
        OperationalScreeningRunResult screeningRun = completedRun();
        when(screeningRunService.run()).thenReturn(screeningRun);
        when(targetSelector.select(screeningResult)).thenReturn(selection);
        when(reconciliationService.reconcile(selection))
                .thenReturn(reconciliation);
        return service().run();
    }

    private OperationalRealtimeScreeningLifecycleService service() {
        return new OperationalRealtimeScreeningLifecycleService(
                screeningRunService, screeningSnapshotRegistry,
                targetSelector, selectionRegistry, reconciliationService);
    }

    private OperationalScreeningRunResult completedRun() {
        OperationalScreeningRunResult result =
                org.mockito.Mockito.mock(OperationalScreeningRunResult.class);
        when(result.status()).thenReturn(
                OperationalScreeningRunStatus.COMPLETED);
        when(result.screeningResult()).thenReturn(Optional.of(screeningResult));
        return result;
    }

    private OperationalScreeningRunResult screeningRun(
            OperationalScreeningRunStatus status
    ) {
        OperationalScreeningRunResult result =
                org.mockito.Mockito.mock(OperationalScreeningRunResult.class);
        when(result.status()).thenReturn(status);
        return result;
    }

    private OperationalRealtimeTargetSelection selection() {
        return org.mockito.Mockito.mock(
                OperationalRealtimeTargetSelection.class);
    }

    private RealtimeTargetReconciliationResult reconciliation(
            RealtimeTargetReconciliationStatus status
    ) {
        RealtimeTargetReconciliationResult result = org.mockito.Mockito.mock(
                RealtimeTargetReconciliationResult.class);
        org.mockito.Mockito.lenient().when(result.status()).thenReturn(status);
        return result;
    }
}
