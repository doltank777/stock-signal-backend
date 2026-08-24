package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalRealtimeTargetSelectorTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 21);
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    private final OperationalRealtimeTargetSelector selector =
            new OperationalRealtimeTargetSelector();

    @Test
    void emptyCandidatesProduceEmptySelection() {
        var selection = selector.select(result(List.of()));

        assertThat(selection.capacity()).isEqualTo(40);
        assertThat(selection.uniqueCandidateCount()).isZero();
        assertThat(selection.selectedCount()).isZero();
        assertThat(selection.excludedCount()).isZero();
    }

    @Test
    void selectionCountsCoverThirtyNineFortyFortyOneAndOneHundred() {
        assertCounts(39, 39, 0);
        assertCounts(40, 40, 0);
        assertCounts(41, 40, 1);
        assertCounts(100, 40, 60);
    }

    @Test
    void mergesStocksAndKeepsOnlyRealtimeConditionsInRankOrder() {
        Stock first = stock(1L, "005930");
        Stock duplicate = stock(1L, "005930");
        var selection = selector.select(result(List.of(
                candidate(first,
                        match(30L, "low", 100, 100, true),
                        match(40L, "disabled", 1000, 100, false)),
                candidate(duplicate,
                        match(20L, "best-score", 300, 90, true),
                        match(10L, "best-tie", 300, 90, true),
                        match(30L, "low", 100, 100, true)))));

        assertThat(selection.selectedTargets()).singleElement()
                .satisfies(target -> {
                    assertThat(target.effectivePriority()).isEqualTo(300);
                    assertThat(target.effectiveScreeningScore()).isEqualTo(90);
                    assertThat(target.matchedConditions())
                            .extracting(DesiredRealtimeCondition::searchConditionId)
                            .containsExactly(10L, 20L, 30L);
                });
    }

    @Test
    void candidatesWithOnlyNonRealtimeMatchesAreExcludedBeforeRanking() {
        var selection = selector.select(result(List.of(
                candidate(stock(1L, "000001"),
                        match(1L, "disabled", 1000, 100, false)),
                candidate(stock(2L, "000002"),
                        match(2L, "enabled", 1, 1, true)))));

        assertThat(selection.uniqueCandidateCount()).isEqualTo(1);
        assertThat(selection.selectedTargets())
                .extracting(DesiredRealtimeTarget::stockCode)
                .containsExactly("000002");
    }

    @Test
    void priorityThenScoreThenStockCodeDetermineOrder() {
        var selection = selector.select(result(List.of(
                candidate(stock(1L, "000003"),
                        match(1L, "score-low", 300, 50, true)),
                candidate(stock(2L, "000002"),
                        match(2L, "priority-low", 250, 100, true)),
                candidate(stock(3L, "000001"),
                        match(3L, "tie-a", 300, 50, true)),
                candidate(stock(4L, "000004"),
                        match(4L, "score-high", 300, 90, true)))));

        assertThat(selection.selectedTargets())
                .extracting(DesiredRealtimeTarget::stockCode)
                .containsExactly("000004", "000001", "000003", "000002");
    }

    @Test
    void inputOrderDoesNotChangeSelectionOrCutoff() {
        List<ScreeningCandidate> candidates = candidates(41);
        var forward = selector.select(result(candidates));
        List<ScreeningCandidate> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);
        var backward = selector.select(result(reversed));

        assertThat(backward).isEqualTo(forward);
        assertThat(forward.selectedTargets())
                .extracting(DesiredRealtimeTarget::stockCode)
                .containsExactlyElementsOf(codes(0, 40));
        assertThat(forward.excludedTargets())
                .extracting(DesiredRealtimeTarget::stockCode)
                .containsExactly("000040");
    }

    private void assertCounts(int candidates, int selected, int excluded) {
        var selection = selector.select(result(candidates(candidates)));
        assertThat(selection.uniqueCandidateCount()).isEqualTo(candidates);
        assertThat(selection.selectedCount()).isEqualTo(selected);
        assertThat(selection.excludedCount()).isEqualTo(excluded);
    }

    private List<ScreeningCandidate> candidates(int count) {
        List<ScreeningCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            candidates.add(candidate(
                    stock((long) index + 1, code(index)),
                    match((long) index + 1, "condition-" + index,
                            100, 50, true)));
        }
        return candidates;
    }

    private List<String> codes(int start, int end) {
        List<String> codes = new ArrayList<>();
        for (int index = start; index < end; index++) {
            codes.add(code(index));
        }
        return codes;
    }

    private String code(int index) {
        return String.format("%06d", index);
    }

    private ScreeningRunResult result(List<ScreeningCandidate> candidates) {
        return new ScreeningRunResult(
                BASE_DATE, NOW, NOW, candidates.size(), candidates.size(),
                candidates, List.of());
    }

    private ScreeningCandidate candidate(
            Stock stock,
            ScreeningMatch... matches
    ) {
        return new ScreeningCandidate(stock, BASE_DATE, List.of(matches));
    }

    private ScreeningMatch match(
            Long id,
            String name,
            int priority,
            int score,
            boolean realtimeEnabled
    ) {
        SearchCondition condition = mock(SearchCondition.class);
        when(condition.getId()).thenReturn(id);
        when(condition.getName()).thenReturn(name);
        return new ScreeningMatch(
                condition, score, priority, realtimeEnabled);
    }

    private Stock stock(Long id, String stockCode) {
        return Stock.builder()
                .id(id)
                .stockCode(stockCode)
                .stockName("stock-" + stockCode)
                .marketType(MarketType.KOSPI)
                .build();
    }
}
