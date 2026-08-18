package com.stockapp.domain.screening.realtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeWatchTargetTest {

    @Test
    void preservesConditionOrderAndRemovesDuplicates() {
        RealtimeWatchTarget target = new RealtimeWatchTarget(
                1L, "478340", List.of(3L, 1L, 3L, 2L));

        assertThat(target.conditionIds()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void defensivelyCopiesConditionIds() {
        List<Long> source = new ArrayList<>(List.of(1L));
        RealtimeWatchTarget target = new RealtimeWatchTarget(
                1L, "0088M0", source);

        source.add(2L);

        assertThat(target.conditionIds()).containsExactly(1L);
        assertThatThrownBy(() -> target.conditionIds().add(2L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidRequiredValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RealtimeWatchTarget(
                        null, "005930", List.of(1L)))
                .withMessage("stockId is required");
        assertThatNullPointerException()
                .isThrownBy(() -> new RealtimeWatchTarget(
                        1L, null, List.of(1L)))
                .withMessage("stockCode is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealtimeWatchTarget(
                        1L, " ", List.of(1L)))
                .withMessage("stockCode must not be blank");
        assertThatNullPointerException()
                .isThrownBy(() -> new RealtimeWatchTarget(
                        1L, "005930", null))
                .withMessage("conditionIds are required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RealtimeWatchTarget(
                        1L, "005930", List.of()))
                .withMessage("at least one conditionId is required");
        assertThatNullPointerException()
                .isThrownBy(() -> new RealtimeWatchTarget(
                        1L, "005930", Arrays.asList(1L, null)))
                .withMessage("conditionId is required");
    }
}
