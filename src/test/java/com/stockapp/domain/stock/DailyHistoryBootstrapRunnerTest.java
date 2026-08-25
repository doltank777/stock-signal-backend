package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryStockSummary;
import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillFailure;
import com.stockapp.domain.stock.dto.KisDailyPriceRequestChunk;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyHistoryBootstrapRunnerTest {

    private static final LocalDate EVALUATION_DATE =
            LocalDate.of(2026, 8, 24);

    private final BootstrapDailyHistoryBatchService batchService =
            mock(BootstrapDailyHistoryBatchService.class);
    private final DailyHistoryBootstrapRunner runner =
            new DailyHistoryBootstrapRunner(batchService);

    @Test
    void returnsReadyResultAfterOneBatchExecution() {
        BootstrapDailyHistoryBatchResult result = result(true);
        when(batchService.bootstrap()).thenReturn(result);

        assertThat(runner.execute()).isSameAs(result);
        verify(batchService).bootstrap();
    }

    @Test
    void notReadyResultProducesFailureExitSemantics() {
        BootstrapDailyHistoryBatchResult result = result(false);
        when(batchService.bootstrap()).thenReturn(result);

        assertThatExceptionOfType(DailyHistoryBootstrapNotReadyException.class)
                .isThrownBy(runner::execute)
                .satisfies(exception -> assertThat(exception.getResult())
                        .isSameAs(result));
        verify(batchService).bootstrap();
    }

    @Test
    void propagatesUnexpectedBatchException() {
        IllegalStateException failure =
                new IllegalStateException("calendar unavailable");
        when(batchService.bootstrap()).thenThrow(failure);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(runner::execute)
                .isSameAs(failure);
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
