package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreeningCandidateTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);

    @Test
    void preservesMultipleMatchesInInputOrder() {
        ScreeningMatch first = match("first", 80, 100, true);
        ScreeningMatch second = match("second", 90, 50, false);

        ScreeningCandidate candidate = new ScreeningCandidate(
                stock(), BASE_DATE, List.of(first, second));

        assertThat(candidate.stock().getStockCode()).isEqualTo("005930");
        assertThat(candidate.baseDate()).isEqualTo(BASE_DATE);
        assertThat(candidate.matches()).containsExactly(first, second);
        assertThat(candidate.matches().get(1).realtimeEnabled()).isFalse();
    }

    @Test
    void rejectsMissingRequiredValues() {
        ScreeningMatch match = match("condition", 80, 100, true);

        assertThatNullPointerException()
                .isThrownBy(() -> new ScreeningCandidate(
                        null, BASE_DATE, List.of(match)))
                .withMessage("stock is required");
        assertThatNullPointerException()
                .isThrownBy(() -> new ScreeningCandidate(
                        stock(), null, List.of(match)))
                .withMessage("baseDate is required");
        assertThatNullPointerException()
                .isThrownBy(() -> new ScreeningCandidate(
                        stock(), BASE_DATE, null))
                .withMessage("matches are required");
    }

    @Test
    void rejectsEmptyOrNullContainingMatches() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScreeningCandidate(
                        stock(), BASE_DATE, List.of()))
                .withMessage("at least one match is required");

        List<ScreeningMatch> matches = Arrays.asList(
                match("condition", 80, 100, true), null);
        assertThatNullPointerException()
                .isThrownBy(() -> new ScreeningCandidate(
                        stock(), BASE_DATE, matches));
    }

    @Test
    void defensivelyCopiesMatchesAndExposesUnmodifiableList() {
        ScreeningMatch first = match("first", 80, 100, true);
        List<ScreeningMatch> source = new ArrayList<>(List.of(first));
        ScreeningCandidate candidate = new ScreeningCandidate(
                stock(), BASE_DATE, source);

        source.add(match("second", 90, 50, false));

        assertThat(candidate.matches()).containsExactly(first);
        assertThatThrownBy(() -> candidate.matches().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ScreeningMatch match(
            String name, int score, int priority, boolean realtimeEnabled) {
        SearchCondition condition = SearchCondition.create(
                name, null, true, priority, score, realtimeEnabled, null);
        return new ScreeningMatch(
                condition, score, priority, realtimeEnabled);
    }

    private Stock stock() {
        return Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build();
    }
}
