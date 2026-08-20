package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionSnapshot;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationRecoveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPriceFinalizationStartupRecoveryRunnerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    @Test
    void doesNothingWithoutLatestExecution() {
        var service = mock(DailyPriceFinalizationRecoveryService.class);
        when(service.findLatestExecution()).thenReturn(Optional.empty());

        runner(service).run(new DefaultApplicationArguments());

        verify(service, never()).recover(DATE);
    }

    @Test
    void doesNothingWhenLatestExecutionIsReady() {
        var service = mock(DailyPriceFinalizationRecoveryService.class);
        when(service.findLatestExecution()).thenReturn(
                Optional.of(snapshot(true)));

        runner(service).run(new DefaultApplicationArguments());

        verify(service, never()).recover(DATE);
    }

    @Test
    void recoversOnlyLatestIncompleteExecution() {
        var service = mock(DailyPriceFinalizationRecoveryService.class);
        var incomplete = snapshot(false);
        when(service.findLatestExecution()).thenReturn(Optional.of(incomplete));
        when(service.recover(DATE)).thenReturn(
                new DailyPriceFinalizationRecoveryResult(false, incomplete));

        runner(service).run(new DefaultApplicationArguments());

        verify(service).recover(DATE);
    }

    @Test
    void recoveryFailureDoesNotFailApplicationStartup() {
        var service = mock(DailyPriceFinalizationRecoveryService.class);
        when(service.findLatestExecution()).thenReturn(
                Optional.of(snapshot(false)));
        when(service.recover(DATE)).thenThrow(new IllegalStateException("fatal"));

        runner(service).run(new DefaultApplicationArguments());

        verify(service).recover(DATE);
    }

    @Test
    void executionLookupFailureDoesNotFailApplicationStartup() {
        var service = mock(DailyPriceFinalizationRecoveryService.class);
        when(service.findLatestExecution()).thenThrow(
                new IllegalStateException("execution table unavailable"));

        runner(service).run(new DefaultApplicationArguments());

        verify(service, never()).recover(DATE);
    }

    private DailyPriceFinalizationStartupRecoveryRunner runner(
            DailyPriceFinalizationRecoveryService service) {
        return new DailyPriceFinalizationStartupRecoveryRunner(service);
    }

    private DailyPriceFinalizationExecutionSnapshot snapshot(boolean ready) {
        return new DailyPriceFinalizationExecutionSnapshot(
                1L, DATE, ready
                ? DailyPriceFinalizationExecutionStatus.COMPLETED
                : DailyPriceFinalizationExecutionStatus.RUNNING,
                ready, 1, Instant.EPOCH, ready ? Instant.EPOCH : null,
                1, 1, 0, 0, 0, 0, 1, 1, 0, null);
    }
}
