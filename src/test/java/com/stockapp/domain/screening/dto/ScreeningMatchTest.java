package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.SearchCondition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ScreeningMatchTest {

    @Test
    void preservesConditionValuesWithoutCalculatingThem() {
        SearchCondition condition = condition();

        ScreeningMatch match = new ScreeningMatch(
                condition, 80, 100, true);
        ScreeningMatch nonRealtimeMatch = new ScreeningMatch(
                condition, -1, -2, false);

        assertThat(match.condition()).isSameAs(condition);
        assertThat(match.screeningScore()).isEqualTo(80);
        assertThat(match.priority()).isEqualTo(100);
        assertThat(match.realtimeEnabled()).isTrue();
        assertThat(nonRealtimeMatch.screeningScore()).isEqualTo(-1);
        assertThat(nonRealtimeMatch.priority()).isEqualTo(-2);
        assertThat(nonRealtimeMatch.realtimeEnabled()).isFalse();
    }

    @Test
    void rejectsNullCondition() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ScreeningMatch(null, 80, 100, true))
                .withMessage("condition is required");
    }

    private SearchCondition condition() {
        return SearchCondition.create(
                "condition", null, true, 100, 80, true, null);
    }
}
