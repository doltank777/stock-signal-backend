package com.stockbatch.kismasterreconciliation;

import com.stockapp.domain.stock.KisMasterSyncExecution;
import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.KisMasterSyncExecutionStatus;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.external.kis.master.KisMasterClient;
import com.stockapp.external.kis.master.KisMasterIdentityConflict;
import com.stockapp.external.kis.master.KisMasterReconciliationPlan;
import com.stockapp.external.kis.master.KisMasterReconciliationPlanner;
import com.stockapp.external.kis.master.KisMasterReconciliationResult;
import com.stockapp.external.kis.master.KisMasterReconciliationService;
import com.stockapp.external.kis.master.KisMasterSnapshot;
import com.stockapp.external.kis.master.KisMasterSnapshotFactory;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisMasterReconciliationRunnerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T04:00:00Z");

    @Test
    void missingAndInvalidModesRefuseBeforeDownload() {
        for (String mode : new String[]{null, "", "yes", "apply-now"}) {
            Dependencies dependencies = dependencies(mode);

            assertThat(dependencies.runner().execute()).isEmpty();

            verifyNoInteractions(dependencies.client());
            verifyNoInteractions(dependencies.executionRepository());
            verifyNoInteractions(dependencies.reconciliationService());
        }
    }

    @Test
    void dryRunDownloadsPlansAndNeverCallsWritePath() {
        Dependencies dependencies = dependencies("dry-run");
        stubSnapshotsAndPlan(dependencies, plan(true, 0, 0));

        Optional<KisMasterReconciliationResult> result =
                dependencies.runner().execute();

        assertThat(result).isEmpty();
        verify(dependencies.client()).downloadAndParse(MarketType.KOSPI);
        verify(dependencies.client()).downloadAndParse(MarketType.KOSDAQ);
        verify(dependencies.planner()).plan(any(), any());
        verifyNoInteractions(dependencies.executionRepository());
        verifyNoInteractions(dependencies.reconciliationService());
    }

    @Test
    void applyIsBlockedBeforeExecutionInsertWhenPlanIsUnsafe() {
        Dependencies dependencies = dependencies("apply");
        stubSnapshotsAndPlan(dependencies, plan(false, 1, 0));

        assertThatThrownBy(() -> dependencies.runner().execute())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPLY blocked");

        verify(dependencies.executionRepository(), never()).saveAndFlush(any());
        verifyNoInteractions(dependencies.reconciliationService());
    }

    @Test
    void explicitSafeApplyCreatesExecutionAndDelegatesToStep1eService() {
        Dependencies dependencies = dependencies("apply");
        KisMasterSnapshot kospi = mock(KisMasterSnapshot.class);
        KisMasterSnapshot kosdaq = mock(KisMasterSnapshot.class);
        when(dependencies.client().downloadAndParse(any())).thenReturn(mock());
        when(dependencies.snapshotFactory().create(eq(MarketType.KOSPI), any()))
                .thenReturn(kospi);
        when(dependencies.snapshotFactory().create(eq(MarketType.KOSDAQ), any()))
                .thenReturn(kosdaq);
        when(dependencies.planner().plan(kospi, kosdaq)).thenReturn(plan(true, 0, 0));
        KisMasterSyncExecution execution = mock(KisMasterSyncExecution.class);
        when(execution.getId()).thenReturn(9L);
        when(execution.getStatus()).thenReturn(KisMasterSyncExecutionStatus.COMPLETED);
        when(execution.getObservedAt()).thenReturn(NOW);
        when(dependencies.executionRepository().saveAndFlush(any())).thenReturn(execution);
        when(dependencies.executionRepository().findById(9L)).thenReturn(Optional.of(execution));
        KisMasterReconciliationResult expected = new KisMasterReconciliationResult(
                2, 1, 0, 1, 0, 0, 0, 0, 1, 0, 1);
        when(dependencies.reconciliationService().reconcile(kospi, kosdaq, execution))
                .thenReturn(expected);

        assertThat(dependencies.runner().execute()).contains(expected);

        verify(dependencies.executionRepository()).saveAndFlush(any());
        verify(dependencies.reconciliationService()).reconcile(kospi, kosdaq, execution);
    }

    private void stubSnapshotsAndPlan(
            Dependencies dependencies,
            KisMasterReconciliationPlan plan
    ) {
        KisMasterSnapshot kospi = mock(KisMasterSnapshot.class);
        KisMasterSnapshot kosdaq = mock(KisMasterSnapshot.class);
        when(dependencies.client().downloadAndParse(any())).thenReturn(mock());
        when(dependencies.snapshotFactory().create(eq(MarketType.KOSPI), any()))
                .thenReturn(kospi);
        when(dependencies.snapshotFactory().create(eq(MarketType.KOSDAQ), any()))
                .thenReturn(kosdaq);
        when(dependencies.planner().plan(kospi, kosdaq)).thenReturn(plan);
    }

    private KisMasterReconciliationPlan plan(
            boolean ready,
            int conflicts,
            int running
    ) {
        return new KisMasterReconciliationPlan(
                ready, ready, 2, 1, 1, 0, 0, 0,
                0, 0, 0, running, List.of(), List.of(),
                conflicts == 0 ? List.of() : List.of(
                        new KisMasterIdentityConflict(
                                "000001", "KR7000000001", 1L,
                                "000002", "KR7000000001")));
    }

    private Dependencies dependencies(String mode) {
        KisMasterClient client = mock(KisMasterClient.class);
        KisMasterSnapshotFactory snapshotFactory = mock(KisMasterSnapshotFactory.class);
        KisMasterReconciliationPlanner planner = mock(KisMasterReconciliationPlanner.class);
        KisMasterSyncExecutionRepository executionRepository =
                mock(KisMasterSyncExecutionRepository.class);
        KisMasterReconciliationService service = mock(KisMasterReconciliationService.class);
        KisMasterReconciliationRunner runner = new KisMasterReconciliationRunner(
                new KisMasterReconciliationProperties(mode), client, snapshotFactory,
                planner, executionRepository, service,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Dependencies(
                runner, client, snapshotFactory, planner, executionRepository, service);
    }

    private record Dependencies(
            KisMasterReconciliationRunner runner,
            KisMasterClient client,
            KisMasterSnapshotFactory snapshotFactory,
            KisMasterReconciliationPlanner planner,
            KisMasterSyncExecutionRepository executionRepository,
            KisMasterReconciliationService reconciliationService
    ) {
    }
}
