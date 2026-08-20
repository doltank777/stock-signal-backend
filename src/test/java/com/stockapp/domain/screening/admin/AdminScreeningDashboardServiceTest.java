package com.stockapp.domain.screening.admin;

import com.stockapp.domain.screening.LatestScreeningSnapshot;
import com.stockapp.domain.screening.LatestScreeningSnapshotRegistry;
import com.stockapp.domain.screening.admin.dto.AdminRealtimeWatchStatusResponse;
import com.stockapp.domain.screening.admin.dto.AdminScreeningResultsResponse;
import com.stockapp.domain.screening.realtime.RealtimeWatchTarget;
import com.stockapp.domain.screening.realtime.RealtimeWatchTargetRegistry;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminScreeningDashboardServiceTest {

    @Mock LatestScreeningSnapshotRegistry snapshotRegistry;
    @Mock RealtimeWatchTargetRegistry targetRegistry;
    @Mock StockRepository stockRepository;

    private AdminScreeningDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminScreeningDashboardService(
                snapshotRegistry, targetRegistry, stockRepository);
    }

    @Test
    void groupsSameStockIntoEveryMatchedConditionAndSortsDeterministically() {
        LatestScreeningSnapshot.Match conditionA = match(1L, "A", 900, true);
        LatestScreeningSnapshot.Match conditionB = match(2L, "B", 800, false);
        LatestScreeningSnapshot snapshot = new LatestScreeningSnapshot(
                LocalDate.of(2026, 8, 19),
                List.of(
                        candidate(2L, "000660", "SK하이닉스", conditionA),
                        candidate(1L, "005930", "삼성전자", conditionA, conditionB)));
        when(snapshotRegistry.findLatest()).thenReturn(Optional.of(snapshot));

        AdminScreeningResultsResponse response = service.getScreeningResults();

        assertThat(response.available()).isTrue();
        assertThat(response.conditions()).extracting("searchConditionName")
                .containsExactly("A", "B");
        assertThat(response.conditions().getFirst().stocks())
                .extracting("stockCode", "stockName", "market")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "000660", "SK하이닉스", MarketType.KOSPI),
                        org.assertj.core.groups.Tuple.tuple(
                                "005930", "삼성전자", MarketType.KOSPI));
        assertThat(response.conditions().get(1).stocks())
                .extracting("stockCode").containsExactly("005930");
    }

    @Test
    void returnsUnavailableResponseWhenScreeningHasNotRun() {
        when(snapshotRegistry.findLatest()).thenReturn(Optional.empty());
        assertThat(service.getScreeningResults())
                .satisfies(response -> {
                    assertThat(response.available()).isFalse();
                    assertThat(response.conditions()).isEmpty();
                });
    }

    @Test
    void bulkLoadsRealtimeStocksAndReturnsCountAndCapacity() {
        when(targetRegistry.findAll()).thenReturn(Map.of(
                "005930", new RealtimeWatchTarget(1L, "005930", List.of(3L, 7L)),
                "000660", new RealtimeWatchTarget(2L, "000660", List.of(3L))));
        when(stockRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(
                stock(1L, "005930", "삼성전자"),
                stock(2L, "000660", "SK하이닉스")));

        AdminRealtimeWatchStatusResponse response = service.getRealtimeWatchStatus();

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.capacity()).isEqualTo(40);
        assertThat(response.stocks()).extracting("stockCode")
                .containsExactly("000660", "005930");
        verify(stockRepository).findAllById(List.of(2L, 1L));
    }

    @Test
    void emptyRegistryDoesNotQueryStocks() {
        when(targetRegistry.findAll()).thenReturn(Map.of());
        assertThat(service.getRealtimeWatchStatus())
                .satisfies(response -> {
                    assertThat(response.count()).isZero();
                    assertThat(response.stocks()).isEmpty();
                });
        verifyNoInteractions(stockRepository);
    }

    private LatestScreeningSnapshot.Candidate candidate(
            Long id, String code, String name,
            LatestScreeningSnapshot.Match... matches) {
        return new LatestScreeningSnapshot.Candidate(
                id, code, name, MarketType.KOSPI, List.of(matches));
    }

    private LatestScreeningSnapshot.Match match(
            Long id, String name, int priority, boolean realtimeEnabled) {
        return new LatestScreeningSnapshot.Match(
                id, name, priority, realtimeEnabled);
    }

    private Stock stock(Long id, String code, String name) {
        return Stock.builder().id(id).stockCode(code).stockName(name)
                .marketType(MarketType.KOSPI).build();
    }
}
