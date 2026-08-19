package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.screening.SearchConditionRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeSignalConditionDefinitionLoaderTest {

    @Test
    void usesOneBatchQueryAndConvertsOnlySignalRulesInOrder() {
        SearchConditionRepository repository = mock(SearchConditionRepository.class);
        SearchCondition condition = mock(SearchCondition.class);
        when(condition.getId()).thenReturn(3L);
        SearchConditionRule screening = valueRule(ScreeningStage.SCREENING, 1);
        SearchConditionRule signal2 = valueRule(ScreeningStage.SIGNAL, 2);
        SearchConditionRule signal1 = SearchConditionRule.createValueRule(
                ScreeningStage.SIGNAL, ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN, BigDecimal.ZERO, null, 1);
        when(condition.getRules()).thenReturn(List.of(signal2, screening, signal1));
        when(repository
                .findAllByIdInAndEnabledTrueAndRealtimeEnabledTrueAndDeletedAtIsNull(
                        List.of(3L, 1L)))
                .thenReturn(List.of(condition));
        RealtimeSignalConditionDefinitionLoader loader =
                new RealtimeSignalConditionDefinitionLoader(repository);

        List<RealtimeSignalConditionDefinition> definitions =
                loader.load(List.of(3L, 1L));

        assertThat(definitions).singleElement().satisfies(definition -> {
            assertThat(definition.conditionId()).isEqualTo(3L);
            assertThat(definition.rules())
                    .extracting(RealtimeSignalRule::ruleOrder)
                    .containsExactly(1, 2);
        });
        verify(repository)
                .findAllByIdInAndEnabledTrueAndRealtimeEnabledTrueAndDeletedAtIsNull(
                        List.of(3L, 1L));
    }

    private SearchConditionRule valueRule(ScreeningStage stage, int order) {
        return SearchConditionRule.createValueRule(
                stage, ScreeningMetric.CURRENT_PRICE, null,
                ScreeningOperator.GREATER_THAN, BigDecimal.ZERO,
                order == 1 ? null
                        : com.stockapp.domain.screening.ScreeningLogicalOperator.AND,
                order);
    }
}
