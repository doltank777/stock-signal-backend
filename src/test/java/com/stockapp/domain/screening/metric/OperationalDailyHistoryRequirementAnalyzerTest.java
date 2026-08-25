package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.signal.realtime.RealtimeSignalConditionDefinition;
import com.stockapp.domain.signal.realtime.RealtimeSignalConditionDefinitionLoader;
import com.stockapp.domain.signal.realtime.RealtimeSignalRequirementAnalyzer;
import com.stockapp.domain.signal.realtime.RealtimeSignalRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalDailyHistoryRequirementAnalyzerTest {

    private final SearchConditionRepository repository =
            mock(SearchConditionRepository.class);
    private final RealtimeSignalConditionDefinitionLoader definitionLoader =
            mock(RealtimeSignalConditionDefinitionLoader.class);
    private final OperationalDailyHistoryRequirementAnalyzer analyzer =
            new OperationalDailyHistoryRequirementAnalyzer(
                    repository,
                    new OperationalScreeningDataRequirementAnalyzer(
                            new ScreeningDataRequirementAnalyzer()),
                    definitionLoader,
                    new RealtimeSignalRequirementAnalyzer());

    @Test
    void emptyActiveConditionSetRequiresOnlyEvaluationDateRow() {
        when(repository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of());

        OperationalDailyHistoryRequirement result = analyzer.analyze();

        assertThat(result).isEqualTo(requirement(0, 0, 0));
        assertThat(result.requiredRowCountIncludingEvaluationDate())
                .isEqualTo(1);
        verify(definitionLoader, never()).load(
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void screeningMovingAverageRequiresEvaluationDateAndTwentyPreviousDays() {
        SearchCondition condition = condition(1L, false,
                screeningRule(ScreeningMetric.MOVING_AVERAGE, 20));
        when(repository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(condition));

        assertThat(analyzer.analyze()).isEqualTo(requirement(20, 20, 0));
    }

    @Test
    void screeningChangeRateRequiresOnePreviousDay() {
        SearchCondition condition = condition(1L, false,
                screeningRule(ScreeningMetric.CHANGE_RATE, null));
        when(repository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(condition));

        assertThat(analyzer.analyze()).isEqualTo(
                new OperationalDailyHistoryRequirement(
                        0, 1, 0, 0, 1, true));
    }

    @Test
    void combinesScreeningAndSignalUsingLargerSignalPeriod() {
        SearchCondition condition = condition(10L, true,
                screeningRule(ScreeningMetric.MOVING_AVERAGE, 20));
        prepareSignal(condition, definition(10L, signalRule(
                ScreeningMetric.MOVING_AVERAGE, 60)));

        assertThat(analyzer.analyze()).isEqualTo(requirement(20, 20, 60));
    }

    @Test
    void combinesScreeningAndSignalUsingLargerScreeningPeriod() {
        SearchCondition condition = condition(10L, true,
                screeningRule(ScreeningMetric.MOVING_AVERAGE, 120));
        prepareSignal(condition, definition(10L, signalRule(
                ScreeningMetric.MOVING_AVERAGE, 20)));

        assertThat(analyzer.analyze()).isEqualTo(requirement(120, 120, 20));
    }

    @Test
    void signalOnlyRequirementUsesRightMetricMaximumPeriod() {
        SearchCondition condition = condition(10L, true,
                screeningRule(ScreeningMetric.CURRENT_PRICE, null));
        RealtimeSignalRule signalRule = new RealtimeSignalRule(
                1, null,
                ScreeningMetric.MOVING_AVERAGE, 20,
                ScreeningOperator.GREATER_THAN,
                com.stockapp.domain.screening.ScreeningRightType.METRIC,
                null,
                ScreeningMetric.MOVING_AVERAGE, 60);
        prepareSignal(condition, definition(10L, signalRule));

        assertThat(analyzer.analyze()).isEqualTo(requirement(0, 0, 60));
    }

    @Test
    void signalChangeRateAddsNoPreviousDayUnderCurrentCalculatorContract() {
        SearchCondition condition = condition(10L, true,
                screeningRule(ScreeningMetric.CURRENT_PRICE, null));
        prepareSignal(condition, definition(10L, signalRule(
                ScreeningMetric.CHANGE_RATE, null)));

        assertThat(analyzer.analyze()).isEqualTo(requirement(0, 0, 0));
    }

    @Test
    void realtimeDisabledConditionContributesScreeningButNotSignalHistory() {
        SearchCondition condition = condition(10L, false,
                screeningRule(ScreeningMetric.MOVING_AVERAGE, 20),
                SearchConditionRule.createValueRule(
                        ScreeningStage.SIGNAL,
                        ScreeningMetric.MOVING_AVERAGE, 200,
                        ScreeningOperator.GREATER_THAN,
                        BigDecimal.ZERO, null, 1));
        when(repository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(condition));

        assertThat(analyzer.analyze()).isEqualTo(requirement(20, 20, 0));
        verify(definitionLoader, never()).load(
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void inactiveAndDeletedConditionsAreExcludedByOperationalRepositoryScope() {
        SearchCondition active = condition(1L, false,
                screeningRule(ScreeningMetric.MOVING_AVERAGE, 20));
        when(repository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(active));

        assertThat(analyzer.analyze()).isEqualTo(requirement(20, 20, 0));
        verify(repository)
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
    }

    private void prepareSignal(
            SearchCondition condition,
            RealtimeSignalConditionDefinition definition
    ) {
        when(repository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(condition));
        when(definitionLoader.load(List.of(condition.getId())))
                .thenReturn(List.of(definition));
    }

    private OperationalDailyHistoryRequirement requirement(
            int screeningMaxPeriod,
            int screeningPreviousCount,
            int signalMaxPeriod
    ) {
        return new OperationalDailyHistoryRequirement(
                screeningMaxPeriod,
                screeningPreviousCount,
                signalMaxPeriod,
                signalMaxPeriod,
                Math.max(screeningPreviousCount, signalMaxPeriod),
                true);
    }

    private SearchCondition condition(
            Long id,
            boolean realtimeEnabled,
            SearchConditionRule... rules
    ) {
        SearchCondition condition = SearchCondition.create(
                "condition", null, true, 100, 80,
                realtimeEnabled, null);
        org.springframework.test.util.ReflectionTestUtils.setField(
                condition, "id", id);
        for (SearchConditionRule rule : rules) {
            condition.addRule(rule);
        }
        return condition;
    }

    private SearchConditionRule screeningRule(
            ScreeningMetric metric,
            Integer period
    ) {
        return SearchConditionRule.createValueRule(
                ScreeningStage.SCREENING,
                metric, period,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ZERO, null, 1);
    }

    private RealtimeSignalRule signalRule(
            ScreeningMetric metric,
            Integer period
    ) {
        return new RealtimeSignalRule(
                1, null, metric, period,
                ScreeningOperator.GREATER_THAN,
                com.stockapp.domain.screening.ScreeningRightType.VALUE,
                BigDecimal.ZERO, null, null);
    }

    private RealtimeSignalConditionDefinition definition(
            Long id,
            RealtimeSignalRule... rules
    ) {
        return new RealtimeSignalConditionDefinition(id, List.of(rules));
    }
}
