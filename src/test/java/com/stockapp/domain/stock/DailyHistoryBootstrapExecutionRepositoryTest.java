package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DailyHistoryBootstrapExecutionRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 24);

    @Autowired
    DailyHistoryBootstrapExecutionRepository repository;

    @Test
    void preservesRepeatedExecutionsAndOrdersLatestDeterministically() {
        DailyHistoryBootstrapExecution first = saveCompleted(
                20, true, Instant.parse("2026-08-24T01:00:00Z"));
        DailyHistoryBootstrapExecution second = saveCompleted(
                120, true, Instant.parse("2026-08-24T02:00:00Z"));
        DailyHistoryBootstrapExecution sameFinish = saveCompleted(
                120, true, Instant.parse("2026-08-24T02:00:00Z"));

        assertThat(repository
                .findByEvaluationDateOrderByFinishedAtDescIdDesc(DATE))
                .extracting(DailyHistoryBootstrapExecution::getId)
                .containsExactly(sameFinish.getId(), second.getId(), first.getId());
        assertThat(repository
                .findFirstByEvaluationDateOrderByFinishedAtDescIdDesc(DATE))
                .get().extracting(DailyHistoryBootstrapExecution::getId)
                .isEqualTo(sameFinish.getId());
    }

    @Test
    void findsLatestReadyExecutionCoveringCurrentRequirement() {
        saveCompleted(20, true, Instant.parse("2026-08-24T01:00:00Z"));
        saveCompleted(
                120, true, Instant.parse("2026-08-24T02:00:00Z"));
        DailyHistoryBootstrapExecution matching = saveCompleted(
                150, true, Instant.parse("2026-08-24T02:30:00Z"));
        saveFailed(200, Instant.parse("2026-08-24T03:00:00Z"));
        saveCompleted(120, true, Instant.parse("2026-08-23T02:00:00Z"),
                DATE.minusDays(1));

        assertThat(repository
                .findFirstByEvaluationDateAndReadyTrueAndRequiredPreviousTradingDayCountGreaterThanEqualOrderByFinishedAtDescIdDesc(
                        DATE, 120))
                .get().extracting(DailyHistoryBootstrapExecution::getId)
                .isEqualTo(matching.getId());
        assertThat(repository
                .findFirstByEvaluationDateAndReadyTrueAndRequiredPreviousTradingDayCountGreaterThanEqualOrderByFinishedAtDescIdDesc(
                        DATE, 151))
                .isEmpty();
    }

    private DailyHistoryBootstrapExecution saveCompleted(
            int requirement, boolean ready, Instant finishedAt) {
        return saveCompleted(requirement, ready, finishedAt, DATE);
    }

    private DailyHistoryBootstrapExecution saveFailed(
            int requirement, Instant finishedAt
    ) {
        DailyHistoryBootstrapExecution execution =
                DailyHistoryBootstrapExecution.create(
                        DATE, requirement, finishedAt.minusSeconds(60));
        execution.fail("IllegalStateException: failed", finishedAt);
        return repository.saveAndFlush(execution);
    }

    private DailyHistoryBootstrapExecution saveCompleted(
            int requirement, boolean ready, Instant finishedAt,
            LocalDate evaluationDate
    ) {
        DailyHistoryBootstrapExecution execution =
                DailyHistoryBootstrapExecution.create(
                        evaluationDate, requirement, finishedAt.minusSeconds(60));
        execution.complete(result(evaluationDate, requirement, ready), finishedAt);
        return repository.saveAndFlush(execution);
    }

    private BootstrapDailyHistoryBatchResult result(
            LocalDate evaluationDate, int requirement, boolean ready
    ) {
        return new BootstrapDailyHistoryBatchResult(
                ready
                        ? BootstrapDailyHistoryBatchStatus.COMPLETED
                        : BootstrapDailyHistoryBatchStatus.COMPLETED_WITH_GAPS,
                evaluationDate, requirement, requirement,
                3, ready ? 3 : 2, ready ? 0 : 1, 0,
                1, ready ? 0 : 1, 1, 1, 1, 1, 1, 0, 0, 0,
                List.of());
    }
}
