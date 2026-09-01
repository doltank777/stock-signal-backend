package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyHistoryBootstrapExecutionStoreTest {
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String POLICY_VERSION = "HISTORY_V1";

    private static final LocalDate DATE = LocalDate.of(2026, 8, 24);
    private static final Instant STARTED = Instant.parse("2026-08-24T00:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-08-24T00:10:00Z");

    private final DailyHistoryBootstrapExecutionRepository repository =
            mock(DailyHistoryBootstrapExecutionRepository.class);
    private final DailyHistoryBootstrapExecutionStore store =
            new DailyHistoryBootstrapExecutionStore(repository);

    @Test
    void startsNewAppendOnlyExecutionIncludingRequirement() {
        when(repository.saveAndFlush(
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var snapshot = store.start(
                DATE, 120, FINGERPRINT, POLICY_VERSION, STARTED);

        ArgumentCaptor<DailyHistoryBootstrapExecution> captor =
                ArgumentCaptor.forClass(DailyHistoryBootstrapExecution.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(snapshot.status())
                .isEqualTo(DailyHistoryBootstrapExecutionStatus.RUNNING);
        assertThat(snapshot.requiredPreviousTradingDayCount()).isEqualTo(120);
        assertThat(captor.getValue().getEvaluationDate()).isEqualTo(DATE);
    }

    @Test
    void completesWithExactBatchAggregatesAndBatchReadiness() {
        DailyHistoryBootstrapExecution execution =
                DailyHistoryBootstrapExecution.create(
                        DATE, 120, FINGERPRINT, POLICY_VERSION, STARTED);
        when(repository.findById(1L)).thenReturn(Optional.of(execution));
        BootstrapDailyHistoryBatchResult result = result(false);

        var snapshot = store.complete(1L, result, FINISHED);

        assertThat(snapshot.status()).isEqualTo(
                DailyHistoryBootstrapExecutionStatus.COMPLETED_WITH_GAPS);
        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.remainingMissingCount()).isEqualTo(1);
        assertThat(snapshot.apiCallCount()).isEqualTo(2);
        assertThat(snapshot.finishedAt()).isEqualTo(FINISHED);
    }

    @Test
    void recordsFailedExecutionWithoutMaskingItsContract() {
        DailyHistoryBootstrapExecution execution =
                DailyHistoryBootstrapExecution.create(
                        DATE, 0, FINGERPRINT, POLICY_VERSION, STARTED);
        when(repository.findById(1L)).thenReturn(Optional.of(execution));

        var snapshot = store.fail(
                1L, new IllegalStateException("auth failure"), FINISHED);

        assertThat(snapshot.status())
                .isEqualTo(DailyHistoryBootstrapExecutionStatus.FAILED);
        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.lastError())
                .isEqualTo("IllegalStateException: auth failure");
    }

    @Test
    void zeroRequirementCompletesAsReadyExecution() {
        DailyHistoryBootstrapExecution execution =
                DailyHistoryBootstrapExecution.create(
                        DATE, 0, FINGERPRINT, POLICY_VERSION, STARTED);
        when(repository.findById(1L)).thenReturn(Optional.of(execution));

        var snapshot = store.complete(1L, result(true, 0), FINISHED);

        assertThat(snapshot.status())
                .isEqualTo(DailyHistoryBootstrapExecutionStatus.COMPLETED);
        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.requiredTradingDateCount()).isZero();
    }

    private BootstrapDailyHistoryBatchResult result(boolean ready) {
        return result(ready, 120);
    }

    private BootstrapDailyHistoryBatchResult result(
            boolean ready, int requirement
    ) {
        return new BootstrapDailyHistoryBatchResult(
                ready
                        ? BootstrapDailyHistoryBatchStatus.COMPLETED
                        : BootstrapDailyHistoryBatchStatus.COMPLETED_WITH_GAPS,
                DATE, requirement, requirement,
                3, ready ? 3 : 2, ready ? 0 : 1, 0,
                5, ready ? 0 : 1, 2, 2, 2, 2, 5, 1, 0, 0,
                List.of());
    }
}
