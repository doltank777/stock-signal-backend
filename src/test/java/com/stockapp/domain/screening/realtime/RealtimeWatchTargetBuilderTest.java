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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeWatchTargetBuilderTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 13);
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    private final RealtimeWatchTargetBuilder builder =
            new RealtimeWatchTargetBuilder();

    @Test
    void excludesCandidatesWithoutRealtimeMatches() {
        ScreeningCandidate candidate = candidate(
                stock(1L, "0088M0"), match(1L, false));

        assertThat(builder.build(result(candidate))).isEmpty();
    }

    @Test
    void keepsOnlyRealtimeMatchesInOriginalOrder() {
        ScreeningCandidate candidate = candidate(
                stock(3L, "478340"),
                match(3L, true),
                match(1L, false),
                match(2L, true));

        assertThat(builder.build(result(candidate)))
                .singleElement()
                .satisfies(target -> {
                    assertThat(target.stockId()).isEqualTo(3L);
                    assertThat(target.stockCode()).isEqualTo("478340");
                    assertThat(target.conditionIds()).containsExactly(3L, 2L);
                });
    }

    @Test
    void mergesDuplicateStockCodesAndConditionIdsInEncounterOrder() {
        Stock first = stock(2L, "012210");
        Stock duplicate = stock(2L, "012210");
        ScreeningCandidate firstCandidate = candidate(
                first, match(1L, true), match(1L, true));
        ScreeningCandidate secondCandidate = candidate(
                duplicate, match(2L, true), match(3L, true), match(1L, true));

        assertThat(builder.build(result(firstCandidate, secondCandidate)))
                .singleElement()
                .satisfies(target -> assertThat(target.conditionIds())
                        .containsExactly(1L, 2L, 3L));
    }

    @Test
    void preservesCandidateOrderAndMultipleConditions() {
        ScreeningCandidate first = candidate(
                stock(1L, "0088M0"), match(1L, true));
        ScreeningCandidate second = candidate(
                stock(3L, "478340"),
                match(3L, true), match(1L, true), match(2L, true));

        List<RealtimeWatchTarget> targets = builder.build(result(first, second));

        assertThat(targets)
                .extracting(RealtimeWatchTarget::stockCode)
                .containsExactly("0088M0", "478340");
        assertThat(targets.get(1).conditionIds())
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void supportsEmptyRunResult() {
        assertThat(builder.build(result())).isEmpty();
    }

    private ScreeningRunResult result(ScreeningCandidate... candidates) {
        return new ScreeningRunResult(
                BASE_DATE,
                NOW,
                NOW,
                candidates.length,
                candidates.length,
                List.of(candidates),
                List.of());
    }

    private ScreeningCandidate candidate(
            Stock stock,
            ScreeningMatch... matches
    ) {
        return new ScreeningCandidate(
                stock, BASE_DATE, List.of(matches));
    }

    private ScreeningMatch match(Long conditionId, boolean realtimeEnabled) {
        SearchCondition condition = mock(SearchCondition.class);
        when(condition.getId()).thenReturn(conditionId);
        return new ScreeningMatch(condition, 80, 100, realtimeEnabled);
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
