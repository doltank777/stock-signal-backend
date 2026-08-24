package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeWatchTargetMapperTest {

    private final RealtimeWatchTargetMapper mapper =
            new RealtimeWatchTargetMapper();

    @Test
    void mapsScalarTargetAndSortsConditionIds() {
        DesiredRealtimeTarget desired = desiredTarget(
                1L, "005930", List.of(condition(3L), condition(1L)));

        assertThat(mapper.map(desired))
                .isEqualTo(new RealtimeWatchTarget(
                        1L, "005930", List.of(1L, 3L)));
    }

    @Test
    void desiredTargetRejectsDuplicateConditionIds() {
        assertThatThrownBy(() -> desiredTarget(
                1L, "005930", List.of(condition(3L), condition(3L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate searchConditionId");
    }

    private DesiredRealtimeTarget desiredTarget(
            Long stockId,
            String stockCode,
            List<DesiredRealtimeCondition> conditions
    ) {
        DesiredRealtimeCondition first = conditions.getFirst();
        return new DesiredRealtimeTarget(
                stockId, stockCode, "stock-" + stockCode,
                MarketType.KOSPI, first.priority(), first.screeningScore(),
                conditions);
    }

    private DesiredRealtimeCondition condition(Long conditionId) {
        return new DesiredRealtimeCondition(
                conditionId, "condition-" + conditionId, 100, 50);
    }
}
