package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceCompletenessResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationBatchResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPriceFinalizationExecutionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
    private static final Instant START = Instant.parse("2026-08-20T07:20:00Z");
    private static final Instant END = Instant.parse("2026-08-20T08:20:00Z");

    @Test
    void recordsRunningBeforeCompletionAndCompletesReady() {
        var execution = DailyPriceFinalizationExecution.create(DATE, START);

        assertThat(execution.getStatus()).isEqualTo(
                DailyPriceFinalizationExecutionStatus.RUNNING);
        assertThat(execution.getFinishedAt()).isNull();
        assertThat(execution.getAttemptCount()).isEqualTo(1);

        execution.complete(result(true, 0, 0, 3), END);
        assertThat(execution.getStatus()).isEqualTo(
                DailyPriceFinalizationExecutionStatus.COMPLETED);
        assertThat(execution.isReady()).isTrue();
        assertThat(execution.getFinishedAt()).isEqualTo(END);
    }

    @Test
    void completedCanRemainNotReadyAndReadyInvariantIsEnforced() {
        var execution = DailyPriceFinalizationExecution.create(DATE, START);
        execution.complete(result(false, 1, 1, 2), END);
        assertThat(execution.getStatus()).isEqualTo(
                DailyPriceFinalizationExecutionStatus.COMPLETED);
        assertThat(execution.isReady()).isFalse();

        var other = DailyPriceFinalizationExecution.create(DATE, START);
        assertThatThrownBy(() -> other.complete(result(true, 1, 0, 3), END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failureAndInterruptAreTerminalStates() {
        var failed = DailyPriceFinalizationExecution.create(DATE, START);
        failed.fail(DailyPriceFinalizationExecutionStatus.FAILED, "401", END);
        assertThat(failed.getStatus()).isEqualTo(
                DailyPriceFinalizationExecutionStatus.FAILED);
        assertThat(failed.getFinishedAt()).isEqualTo(END);

        var interrupted = DailyPriceFinalizationExecution.create(DATE, START);
        interrupted.fail(DailyPriceFinalizationExecutionStatus.INTERRUPTED,
                "interrupted", END);
        assertThat(interrupted.getStatus()).isEqualTo(
                DailyPriceFinalizationExecutionStatus.INTERRUPTED);
    }

    private DailyPriceFinalizationExecutionResult result(
            boolean ready, int failed, int missing, int present) {
        var batch = new DailyPriceFinalizationBatchResult(
                DATE, 3, 3 - failed, 0, 0, 0, failed, 3,
                true, START, END, List.of(), List.of(), List.of());
        var completeness = new DailyPriceCompletenessResult(
                DATE, 3, present, missing, failed, ready,
                List.of(), List.of(), List.of());
        return new DailyPriceFinalizationExecutionResult(batch, completeness);
    }
}
