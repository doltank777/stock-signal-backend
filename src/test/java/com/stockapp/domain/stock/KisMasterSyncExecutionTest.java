package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class KisMasterSyncExecutionTest {

    @Test
    void completesOneCombinedKospiKosdaqExecution() {
        Instant startedAt = Instant.parse("2026-09-01T00:00:00Z");
        Instant observedAt = Instant.parse("2026-09-01T00:00:05Z");
        Instant finishedAt = Instant.parse("2026-09-01T00:00:10Z");
        KisMasterSyncExecution execution = KisMasterSyncExecution.create(startedAt);

        execution.complete(new KisMasterSyncExecutionCompletion(
                observedAt, 2571, 1824, 2654, 1741, 0, 0, 0), finishedAt);

        assertThat(execution.getStatus()).isEqualTo(KisMasterSyncExecutionStatus.COMPLETED);
        assertThat(execution.getStartedAt()).isEqualTo(startedAt);
        assertThat(execution.getObservedAt()).isEqualTo(observedAt);
        assertThat(execution.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(execution.getTotalParsedRowCount()).isEqualTo(4395);
        assertThat(execution.getLastError()).isNull();
    }

    @Test
    void recordsFailureWithoutInventingSnapshotCounts() {
        KisMasterSyncExecution execution = KisMasterSyncExecution.create(
                Instant.parse("2026-09-01T00:00:00Z"));

        execution.fail("download failed", Instant.parse("2026-09-01T00:00:10Z"));

        assertThat(execution.getStatus()).isEqualTo(KisMasterSyncExecutionStatus.FAILED);
        assertThat(execution.getObservedAt()).isNull();
        assertThat(execution.getTotalParsedRowCount()).isNull();
        assertThat(execution.getLastError()).isEqualTo("download failed");
    }

    @Test
    void rejectsInvalidCountsAndTerminalTransition() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new KisMasterSyncExecutionCompletion(
                        Instant.parse("2026-09-01T00:00:05Z"),
                        1, 1, 1, 0, 0, 0, 0));

        KisMasterSyncExecution execution = KisMasterSyncExecution.create(
                Instant.parse("2026-09-01T00:00:00Z"));
        execution.fail("failed", Instant.parse("2026-09-01T00:00:10Z"));

        assertThatIllegalStateException().isThrownBy(() -> execution.fail(
                "again", Instant.parse("2026-09-01T00:00:20Z")));
    }
}
