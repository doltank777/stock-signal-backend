package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatestScreeningSnapshotRegistryTest {

    @Test
    void replacesCompletedResultWithDeepImmutableScalarSnapshot() {
        LatestScreeningSnapshotRegistry registry =
                new LatestScreeningSnapshotRegistry();
        Stock stock = stock(1L, "005930", "삼성전자");
        SearchCondition condition = condition(3L, "검색식 A", 900, true);

        registry.replace(result(LocalDate.of(2026, 8, 19), stock, condition));
        stock.updateStockInfo("변경된 이름", MarketType.KOSDAQ);

        LatestScreeningSnapshot snapshot = registry.findLatest().orElseThrow();
        assertThat(snapshot.baseDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(snapshot.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.stockName()).isEqualTo("삼성전자");
            assertThat(candidate.market()).isEqualTo(MarketType.KOSPI);
            assertThat(candidate.matches()).singleElement().satisfies(match -> {
                assertThat(match.searchConditionId()).isEqualTo(3L);
                assertThat(match.searchConditionName()).isEqualTo("검색식 A");
            });
        });
        assertThatThrownBy(() -> snapshot.candidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.candidates().getFirst().matches().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void distinguishesNoResultFromCompletedEmptyResultAndReplacesLatest() {
        LatestScreeningSnapshotRegistry registry =
                new LatestScreeningSnapshotRegistry();
        assertThat(registry.findLatest()).isEmpty();

        ScreeningRunResult empty = new ScreeningRunResult(
                LocalDate.of(2026, 8, 19), Instant.EPOCH, Instant.EPOCH,
                0, 0, List.of(), List.of());
        registry.replace(empty);
        assertThat(registry.findLatest()).get()
                .extracting(LatestScreeningSnapshot::baseDate)
                .isEqualTo(LocalDate.of(2026, 8, 19));

        ScreeningRunResult next = new ScreeningRunResult(
                LocalDate.of(2026, 8, 20), Instant.EPOCH, Instant.EPOCH,
                0, 0, List.of(), List.of());
        registry.replace(next);
        assertThat(registry.findLatest()).get()
                .extracting(LatestScreeningSnapshot::baseDate)
                .isEqualTo(LocalDate.of(2026, 8, 20));
    }

    private ScreeningRunResult result(
            LocalDate baseDate, Stock stock, SearchCondition condition) {
        ScreeningCandidate candidate = new ScreeningCandidate(
                stock, baseDate,
                List.of(new ScreeningMatch(condition, 80, 900, true)));
        return new ScreeningRunResult(
                baseDate, Instant.EPOCH, Instant.EPOCH,
                1, 1, List.of(candidate), List.of());
    }

    private Stock stock(Long id, String code, String name) {
        return Stock.builder().id(id).stockCode(code).stockName(name)
                .marketType(MarketType.KOSPI).build();
    }

    private SearchCondition condition(
            Long id, String name, int priority, boolean realtimeEnabled) {
        SearchCondition condition = SearchCondition.create(
                name, null, true, priority, 80, realtimeEnabled, null);
        ReflectionTestUtils.setField(condition, "id", id);
        return condition;
    }
}
