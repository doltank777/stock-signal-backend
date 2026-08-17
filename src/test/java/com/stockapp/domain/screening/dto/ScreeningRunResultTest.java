package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreeningRunResultTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);
    private static final Instant STARTED_AT = Instant.parse("2026-08-16T23:30:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-08-16T23:31:00Z");

    @Test
    void createsEmptyResultWhenAllStocksWereEvaluatedWithoutMatches() {
        ScreeningRunResult result = result(
                100, 100, List.of(), List.of());

        assertThat(result.baseDate()).isEqualTo(BASE_DATE);
        assertThat(result.startedAt()).isEqualTo(STARTED_AT);
        assertThat(result.finishedAt()).isEqualTo(FINISHED_AT);
        assertThat(result.totalStockCount()).isEqualTo(100);
        assertThat(result.evaluatedStockCount()).isEqualTo(100);
        assertThat(result.candidateStockCount()).isZero();
        assertThat(result.totalMatchCount()).isZero();
        assertThat(result.failedStockCount()).isZero();
    }

    @Test
    void calculatesCountsForCandidatesAndFailures() {
        ScreeningCandidate first = candidate("005930", 2);
        ScreeningCandidate second = candidate("000660", 1);
        ScreeningFailure failure = failure("035420");

        ScreeningRunResult result = result(
                4, 3, List.of(first, second), List.of(failure));

        assertThat(result.candidateStockCount()).isEqualTo(2);
        assertThat(result.totalMatchCount()).isEqualTo(3);
        assertThat(result.failedStockCount()).isEqualTo(1);
        assertThat(result.candidates()).containsExactly(first, second);
        assertThat(result.failures()).containsExactly(failure);
    }

    @Test
    void allowsNoCandidatesOrNoFailuresIndependently() {
        assertThat(result(2, 1, List.of(), List.of(failure("005930")))
                .candidateStockCount()).isZero();
        assertThat(result(2, 2, List.of(candidate("005930", 1)), List.of())
                .failedStockCount()).isZero();
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatNullPointerException().isThrownBy(() ->
                new ScreeningRunResult(null, STARTED_AT, FINISHED_AT,
                        0, 0, List.of(), List.of()));
        assertThatNullPointerException().isThrownBy(() ->
                new ScreeningRunResult(BASE_DATE, null, FINISHED_AT,
                        0, 0, List.of(), List.of()));
        assertThatNullPointerException().isThrownBy(() ->
                new ScreeningRunResult(BASE_DATE, STARTED_AT, null,
                        0, 0, List.of(), List.of()));
        assertThatNullPointerException().isThrownBy(() ->
                new ScreeningRunResult(BASE_DATE, STARTED_AT, FINISHED_AT,
                        0, 0, null, List.of()));
        assertThatNullPointerException().isThrownBy(() ->
                new ScreeningRunResult(BASE_DATE, STARTED_AT, FINISHED_AT,
                        0, 0, List.of(), null));
    }

    @Test
    void rejectsNegativeOrInconsistentCounts() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                result(-1, 0, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                result(0, -1, List.of(), List.of(failure("005930"))));
        assertThatIllegalArgumentException().isThrownBy(() ->
                result(3, 1, List.of(), List.of(failure("005930"))));
        assertThatIllegalArgumentException().isThrownBy(() ->
                result(1, 1,
                        List.of(candidate("005930", 1), candidate("000660", 1)),
                        List.of()));
    }

    @Test
    void allowsEqualTimesAndRejectsFinishedAtBeforeStartedAt() {
        ScreeningRunResult result = new ScreeningRunResult(
                BASE_DATE, STARTED_AT, STARTED_AT,
                0, 0, List.of(), List.of());

        assertThat(result.finishedAt()).isEqualTo(STARTED_AT);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ScreeningRunResult(
                        BASE_DATE, STARTED_AT, STARTED_AT.minusSeconds(1),
                        0, 0, List.of(), List.of()));
    }

    @Test
    void defensivelyCopiesListsAndPreservesTheirOrder() {
        ScreeningCandidate first = candidate("005930", 1);
        ScreeningCandidate second = candidate("000660", 1);
        ScreeningFailure firstFailure = failure("035420");
        ScreeningFailure secondFailure = failure("051910");
        List<ScreeningCandidate> candidates = new ArrayList<>(
                List.of(first, second));
        List<ScreeningFailure> failures = new ArrayList<>(
                List.of(firstFailure, secondFailure));

        ScreeningRunResult result = result(
                4, 2, candidates, failures);
        candidates.clear();
        failures.clear();

        assertThat(result.candidates()).containsExactly(first, second);
        assertThat(result.failures()).containsExactly(
                firstFailure, secondFailure);
        assertThatThrownBy(() -> result.candidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.failures().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullElementsInLists() {
        assertThatNullPointerException().isThrownBy(() -> result(
                1, 1, Arrays.asList(candidate("005930", 1), null), List.of()));
        assertThatNullPointerException().isThrownBy(() -> result(
                1, 0, List.of(), Arrays.asList(failure("005930"), null)));
    }

    private ScreeningRunResult result(
            int total,
            int evaluated,
            List<ScreeningCandidate> candidates,
            List<ScreeningFailure> failures
    ) {
        return new ScreeningRunResult(
                BASE_DATE, STARTED_AT, FINISHED_AT,
                total, evaluated, candidates, failures);
    }

    private ScreeningCandidate candidate(String stockCode, int matchCount) {
        Stock stock = Stock.builder()
                .id(1L)
                .stockCode(stockCode)
                .stockName("stock-" + stockCode)
                .marketType(MarketType.KOSPI)
                .build();
        List<ScreeningMatch> matches = new ArrayList<>();
        for (int index = 0; index < matchCount; index++) {
            SearchCondition condition = SearchCondition.create(
                    "condition-" + index, null, true,
                    100 - index, 80 - index, false, null);
            matches.add(new ScreeningMatch(
                    condition, 80 - index, 100 - index, false));
        }
        return new ScreeningCandidate(stock, BASE_DATE, matches);
    }

    private ScreeningFailure failure(String stockCode) {
        return new ScreeningFailure(
                stockCode, "stock-" + stockCode,
                "EVALUATION_FAILED", "evaluation failed");
    }
}
