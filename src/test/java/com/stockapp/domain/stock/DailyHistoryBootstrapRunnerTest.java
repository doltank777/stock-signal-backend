package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryRequest;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryStockSummary;
import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillFailure;
import com.stockapp.domain.stock.dto.DailyHistoryBootstrapExecutionSnapshot;
import com.stockapp.domain.stock.dto.KisDailyPriceRequestChunk;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyHistoryBootstrapRunnerTest {

    private static final LocalDate EVALUATION_DATE =
            LocalDate.of(2026, 8, 24);
    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final BootstrapDailyHistoryRequest REQUEST =
            new BootstrapDailyHistoryRequest(EVALUATION_DATE, 120);

    private final BootstrapDailyHistoryBatchService batchService =
            mock(BootstrapDailyHistoryBatchService.class);
    private final DailyHistoryBootstrapExecutionStore executionStore =
            mock(DailyHistoryBootstrapExecutionStore.class);
    private final DailyHistoryBootstrapRunner runner =
            new DailyHistoryBootstrapRunner(
                    batchService,
                    executionStore,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void returnsReadyResultAfterOneBatchExecution() {
        BootstrapDailyHistoryBatchResult result = result(true);
        arrangeExecution(result);

        assertThat(runner.execute()).isSameAs(result);
        verify(executionStore).complete(1L, result, NOW);
    }

    @Test
    void notReadyResultProducesFailureExitSemantics() {
        BootstrapDailyHistoryBatchResult result = result(false);
        arrangeExecution(result);

        assertThatExceptionOfType(DailyHistoryBootstrapNotReadyException.class)
                .isThrownBy(runner::execute)
                .satisfies(exception -> assertThat(exception.getResult())
                        .isSameAs(result));
        verify(executionStore).complete(1L, result, NOW);
    }

    @Test
    void propagatesUnexpectedBatchException() {
        IllegalStateException failure =
                new IllegalStateException("calendar unavailable");
        arrangeStart();
        when(batchService.bootstrap(REQUEST)).thenThrow(failure);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(runner::execute)
                .isSameAs(failure);
        verify(executionStore).fail(1L, failure, NOW);
    }

    @Test
    void persistenceFailurePreventsSuccessfulCompletion() {
        BootstrapDailyHistoryBatchResult result = result(true);
        arrangeStart();
        when(batchService.bootstrap(REQUEST)).thenReturn(result);
        IllegalStateException failure =
                new IllegalStateException("execution update failed");
        when(executionStore.complete(1L, result, NOW)).thenThrow(failure);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(runner::execute)
                .isSameAs(failure);
        verify(executionStore).fail(1L, failure, NOW);
    }

    private void arrangeExecution(BootstrapDailyHistoryBatchResult result) {
        arrangeStart();
        when(batchService.bootstrap(REQUEST)).thenReturn(result);
    }

    private void arrangeStart() {
        when(batchService.resolveRequest()).thenReturn(REQUEST);
        when(executionStore.start(EVALUATION_DATE, 120, NOW))
                .thenReturn(executionSnapshot());
    }

    private DailyHistoryBootstrapExecutionSnapshot executionSnapshot() {
        return new DailyHistoryBootstrapExecutionSnapshot(
                1L, EVALUATION_DATE,
                DailyHistoryBootstrapExecutionStatus.RUNNING, false,
                120, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                NOW, null, null);
    }

    private BootstrapDailyHistoryBatchResult result(boolean ready) {
        BootstrapMissingHistoryFetchFillFailure failure = ready
                ? null
                : new BootstrapMissingHistoryFetchFillFailure(
                        new KisDailyPriceRequestChunk(
                                LocalDate.of(2026, 1, 2),
                                LocalDate.of(2026, 5, 29)),
                        "ResourceAccessException", "network", 3);
        List<BootstrapDailyHistoryStockSummary> problems = ready
                ? List.of()
                : List.of(new BootstrapDailyHistoryStockSummary(
                        "005930",
                        BootstrapMissingHistoryFetchFillStatus.FAILED,
                        1,
                        failure));
        return new BootstrapDailyHistoryBatchResult(
                ready
                        ? BootstrapDailyHistoryBatchStatus.COMPLETED
                        : BootstrapDailyHistoryBatchStatus.COMPLETED_WITH_GAPS,
                EVALUATION_DATE,
                120,
                120,
                3,
                ready ? 3 : 2,
                0,
                ready ? 0 : 1,
                5,
                ready ? 0 : 1,
                2,
                2,
                2,
                2,
                5,
                1,
                0,
                0,
                problems);
    }
}
