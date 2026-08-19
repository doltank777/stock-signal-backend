package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSignalConditionDefinitionCacheTest {

    @Test
    void batchLoadsMissingDefinitionsOnceAndReusesImmutableRuntimeValues() {
        RealtimeSignalConditionDefinitionLoader loader =
                mock(RealtimeSignalConditionDefinitionLoader.class);
        RealtimeSignalConditionDefinition one = definition(1L);
        RealtimeSignalConditionDefinition two = definition(2L);
        when(loader.load(List.of(2L, 1L))).thenReturn(List.of(one, two));
        RealtimeSignalConditionDefinitionCache cache =
                new RealtimeSignalConditionDefinitionCache(loader);

        Map<Long, RealtimeSignalConditionDefinition> first =
                cache.getAll(List.of(2L, 1L));
        Map<Long, RealtimeSignalConditionDefinition> second =
                cache.getAll(List.of(1L, 2L));

        assertThat(first).containsEntry(1L, one).containsEntry(2L, two);
        assertThat(second).containsEntry(1L, one).containsEntry(2L, two);
        verify(loader, times(1)).load(List.of(2L, 1L));
        assertThat(one.rules()).isUnmodifiable();
    }

    @Test
    void failsWhenBatchResultOmitsRequestedCondition() {
        RealtimeSignalConditionDefinitionLoader loader =
                mock(RealtimeSignalConditionDefinitionLoader.class);
        when(loader.load(List.of(3L, 1L))).thenReturn(List.of(definition(3L)));
        RealtimeSignalConditionDefinitionCache cache =
                new RealtimeSignalConditionDefinitionCache(loader);

        assertThatIllegalStateException()
                .isThrownBy(() -> cache.getAll(List.of(3L, 1L)))
                .withMessageContaining("1");
    }

    private RealtimeSignalConditionDefinition definition(Long id) {
        return new RealtimeSignalConditionDefinition(id, List.of(
                RealtimeSignalRuleEvaluatorTest.valueRule(
                        ScreeningMetric.CURRENT_PRICE, null,
                        ScreeningOperator.GREATER_THAN,
                        BigDecimal.ZERO, null, 1)));
    }
}
