package com.stockapp.domain.screening;

import com.stockapp.domain.stock.DailyHistoryBootstrapExecutionStore;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.OperationalStockUniverseFingerprint;
import com.stockapp.domain.stock.OperationalStockUniverseService;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.dto.DailyHistoryBootstrapExecutionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyHistoryBootstrapReadinessServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 24);

    private final DailyHistoryBootstrapExecutionStore executionStore =
            mock(DailyHistoryBootstrapExecutionStore.class);
    private final OperationalStockUniverseService universeService =
            mock(OperationalStockUniverseService.class);
    private final OperationalStockUniverseFingerprint fingerprint =
            new OperationalStockUniverseFingerprint();
    private final List<Stock> stocks = List.of(Stock.builder().id(1L)
            .stockCode("005930").stockName("Samsung")
            .marketType(MarketType.KOSPI).build());
    private final String universeFingerprint = fingerprint.calculate(stocks);
    private final DailyHistoryBootstrapReadinessService service =
            new DailyHistoryBootstrapReadinessService(
                    executionStore, universeService, fingerprint);

    @Test
    void noSufficientReadyExecutionFailsClosed() {
        when(universeService.findHistoryTargets()).thenReturn(stocks);
        when(executionStore.findLatestReady(DATE, 120, universeFingerprint,
                OperationalStockUniverseFingerprint.HISTORY_POLICY_VERSION))
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
        when(universeService.findHistoryTargets()).thenReturn(stocks);
        when(executionStore.findLatestReady(DATE, 120, universeFingerprint,
                OperationalStockUniverseFingerprint.HISTORY_POLICY_VERSION))
                .thenReturn(Optional.of(matched));

        var result = service.check(DATE, 120);

        assertThat(result.ready()).isTrue();
        assertThat(result.matchedExecution()).containsSame(matched);
    }

    @Test
    void zeroRequirementStillRequiresPersistedReadyExecution() {
        when(universeService.findHistoryTargets()).thenReturn(stocks);
        when(executionStore.findLatestReady(DATE, 0, universeFingerprint,
                OperationalStockUniverseFingerprint.HISTORY_POLICY_VERSION))
                .thenReturn(Optional.empty());

        assertThat(service.check(DATE, 0).ready()).isFalse();
        verify(executionStore).findLatestReady(DATE, 0, universeFingerprint,
                OperationalStockUniverseFingerprint.HISTORY_POLICY_VERSION);

    }

    @Test
    void sameCountDifferentUniverseDoesNotReuseExecution() {
        List<Stock> changed = List.of(Stock.builder().id(2L)
                .stockCode("000660").stockName("SK Hynix")
                .marketType(MarketType.KOSPI).build());
        String changedFingerprint = fingerprint.calculate(changed);
        when(universeService.findHistoryTargets()).thenReturn(changed);
        when(executionStore.findLatestReady(DATE, 120, changedFingerprint,
                OperationalStockUniverseFingerprint.HISTORY_POLICY_VERSION))
                .thenReturn(Optional.empty());

        assertThat(service.check(DATE, 120).ready()).isFalse();
        assertThat(changedFingerprint).isNotEqualTo(universeFingerprint);
    }
}
