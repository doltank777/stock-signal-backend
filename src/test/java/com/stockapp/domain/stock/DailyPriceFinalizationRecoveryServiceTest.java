package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPriceFinalizationRecoveryServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
    private static final Instant NOW = Instant.parse("2026-08-20T07:20:00Z");
    @Mock DailyPriceFinalizationBatchService batchService;
    @Mock DailyPriceFinalizationExecutionStore store;

    @Test
    void alreadyReadyRecoveryIsNoOp() {
        var ready = snapshot(1L,
                DailyPriceFinalizationExecutionStatus.COMPLETED, true);
        when(store.find(DATE)).thenReturn(Optional.of(ready));
        var service = service(new DailyPriceFinalizationRunGuard());

        var result = service.recover(DATE);

        assertThat(result.alreadyReady()).isTrue();
        verify(batchService, never()).finalizeAllWithinGuard(DATE);
        verify(store, never()).start(DATE, NOW);
    }

    @Test
    void runningResidueIsRerunAndFailureDoesNotLeakGuard() {
        var running = snapshot(1L,
                DailyPriceFinalizationExecutionStatus.RUNNING, false);
        when(store.find(DATE)).thenReturn(Optional.of(running));
        when(store.start(DATE, NOW)).thenReturn(running);
        when(batchService.finalizeAllWithinGuard(DATE))
                .thenThrow(new IllegalStateException("fatal"));
        var guard = new DailyPriceFinalizationRunGuard();
        var service = service(guard);

        assertThatThrownBy(() -> service.recover(DATE))
                .isInstanceOf(IllegalStateException.class);
        verify(store).fail(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(
                        DailyPriceFinalizationExecutionStatus.FAILED),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(NOW));
        guard.acquire();
        guard.release();
    }

    @Test
    void concurrentRunIsRejected() {
        var guard = new DailyPriceFinalizationRunGuard();
        guard.acquire();
        try {
            assertThatThrownBy(() -> service(guard).recover(DATE))
                    .isInstanceOf(
                            DailyPriceFinalizationAlreadyRunningException.class);
        } finally {
            guard.release();
        }
    }

    @Test
    void rejectsFutureTargetDate() {
        assertThatThrownBy(() -> service(new DailyPriceFinalizationRunGuard())
                .recover(DATE.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
        verify(store, never()).start(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private DailyPriceFinalizationRecoveryService service(
            DailyPriceFinalizationRunGuard guard) {
        return new DailyPriceFinalizationRecoveryService(
                batchService, store, guard,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DailyPriceFinalizationExecutionSnapshot snapshot(
            Long id, DailyPriceFinalizationExecutionStatus status,
            boolean ready) {
        return new DailyPriceFinalizationExecutionSnapshot(
                id, DATE, status, ready, 1, NOW, null,
                0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }
}
