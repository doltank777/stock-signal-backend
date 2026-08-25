package com.stockapp.domain.screening;

import com.stockapp.domain.stock.DailyHistoryBootstrapExecutionStore;
import com.stockapp.domain.stock.dto.DailyHistoryBootstrapExecutionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyHistoryBootstrapReadinessServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 24);

    private final DailyHistoryBootstrapExecutionStore executionStore =
            mock(DailyHistoryBootstrapExecutionStore.class);
    private final DailyHistoryBootstrapReadinessService service =
            new DailyHistoryBootstrapReadinessService(executionStore);

    @Test
    void noSufficientReadyExecutionFailsClosed() {
        when(executionStore.findLatestReady(DATE, 120))
                .thenReturn(Optional.empty());

        var result = service.check(DATE, 120);

        assertThat(result.ready()).isFalse();
        assertThat(result.evaluationDate()).isEqualTo(DATE);
        assertThat(result.requiredPreviousTradingDayCount()).isEqualTo(120);
        assertThat(result.matchedExecution()).isEmpty();
    }

    @Test
    void sufficientLatestReadyExecutionPassesEvenAfterLaterFailedAttempt() {
        DailyHistoryBootstrapExecutionSnapshot matched =
                mock(DailyHistoryBootstrapExecutionSnapshot.class);
        when(executionStore.findLatestReady(DATE, 120))
                .thenReturn(Optional.of(matched));

        var result = service.check(DATE, 120);

        assertThat(result.ready()).isTrue();
        assertThat(result.matchedExecution()).containsSame(matched);
    }

    @Test
    void zeroRequirementStillRequiresPersistedReadyExecution() {
        when(executionStore.findLatestReady(DATE, 0))
                .thenReturn(Optional.empty());

        assertThat(service.check(DATE, 0).ready()).isFalse();
        verify(executionStore).findLatestReady(DATE, 0);
    }
}
