package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScreeningDataRequirementAnalyzerTest {

    private final ScreeningDataRequirementAnalyzer analyzer =
            new ScreeningDataRequirementAnalyzer();

    @Test
    void rejectsNullConditionInputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> analyzer.analyze((SearchCondition) null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> analyzer.analyze((List<SearchCondition>) null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> analyzer.analyze(Arrays.asList(condition(
                        valueRule(ScreeningStage.SCREENING,
                                ScreeningMetric.CURRENT_PRICE, null)), null)));
    }

    @Test
    void returnsNoRequirementsForEmptyConditionList() {
        assertThat(analyzer.analyze(List.of()))
                .isEqualTo(new ScreeningDataRequirements(false, 0));
    }

    @ParameterizedTest
    @EnumSource(value = ScreeningMetric.class,
            names = {"CURRENT_PRICE", "CHANGE_RATE", "VOLUME"})
    void analyzesSnapshotOnlyMetric(ScreeningMetric metric) {
        assertThat(analyzer.analyze(condition(valueRule(
                ScreeningStage.SCREENING, metric, 999))))
                .isEqualTo(new ScreeningDataRequirements(true, 0));
    }

    @ParameterizedTest
    @EnumSource(value = ScreeningMetric.class,
            names = {"AVERAGE_VOLUME", "MOVING_AVERAGE"})
    void analyzesDailyOnlyMetric(ScreeningMetric metric) {
        assertThat(analyzer.analyze(condition(valueRule(
                ScreeningStage.SCREENING, metric, 20))))
                .isEqualTo(new ScreeningDataRequirements(false, 20));
    }

    @Test
    void volumeRatioRequiresSnapshotAndDailyHistory() {
        assertThat(analyzer.analyze(condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.VOLUME_RATIO, 60))))
                .isEqualTo(new ScreeningDataRequirements(true, 60));
    }

    @Test
    void includesRightMetricButNotRightValue() {
        SearchCondition valueCondition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null));
        SearchCondition metricCondition = condition(metricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningMetric.MOVING_AVERAGE, 20));

        assertThat(analyzer.analyze(valueCondition))
                .isEqualTo(new ScreeningDataRequirements(true, 0));
        assertThat(analyzer.analyze(metricCondition))
                .isEqualTo(new ScreeningDataRequirements(true, 20));
    }

    @Test
    void excludesSignalRulesFromCombinedRequirements() {
        SearchCondition first = condition(
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.MOVING_AVERAGE, 20),
                valueRule(ScreeningStage.SIGNAL,
                        ScreeningMetric.MOVING_AVERAGE, 120));
        SearchCondition second = condition(
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null),
                valueRule(ScreeningStage.SIGNAL,
                        ScreeningMetric.MOVING_AVERAGE, 240));

        assertThat(analyzer.analyze(List.of(first, second)))
                .isEqualTo(new ScreeningDataRequirements(true, 20));
    }

    @Test
    void combinesAllConditionsUsingLargestDailyPeriod() {
        List<SearchCondition> conditions = List.of(
                condition(valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null)),
                condition(valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.AVERAGE_VOLUME, 5)),
                condition(valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.VOLUME_RATIO, 20)),
                condition(metricRule(ScreeningStage.SCREENING,
                        ScreeningMetric.MOVING_AVERAGE, 60,
                        ScreeningMetric.CURRENT_PRICE, null)));

        assertThat(analyzer.analyze(conditions))
                .isEqualTo(new ScreeningDataRequirements(true, 60));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveDailyPeriod(int period) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                analyzer.analyze(condition(valueRule(
                        ScreeningStage.SCREENING,
                        ScreeningMetric.MOVING_AVERAGE, period))));
    }

    @Test
    void rejectsMissingDailyPeriodAndConditionWithoutScreeningRule() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                analyzer.analyze(condition(valueRule(
                        ScreeningStage.SCREENING,
                        ScreeningMetric.AVERAGE_VOLUME, null))));
        assertThatIllegalArgumentException().isThrownBy(() ->
                analyzer.analyze(condition(valueRule(
                        ScreeningStage.SIGNAL,
                        ScreeningMetric.CURRENT_PRICE, null))));
    }

    @Test
    void ignoresConditionManagementFields() {
        SearchCondition low = condition(
                false, 0, 0, false,
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null));
        SearchCondition high = condition(
                true, 1000, 100, true,
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null));

        assertThat(analyzer.analyze(List.of(low, high)))
                .isEqualTo(new ScreeningDataRequirements(true, 0));
    }

    @Test
    void combinesMaximumPeriodFromLeftAndRightMetricsAndExcludesSignal() {
        SearchCondition condition = condition(
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.AVERAGE_VOLUME, 5),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.MOVING_AVERAGE, 20),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.VOLUME_RATIO, 60),
                metricRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null,
                        ScreeningMetric.MOVING_AVERAGE, 120),
                valueRule(ScreeningStage.SIGNAL,
                        ScreeningMetric.MOVING_AVERAGE, 240));

        assertThat(analyzer.analyze(condition))
                .isEqualTo(new ScreeningDataRequirements(true, 120));
    }

    private SearchCondition condition(SearchConditionRule... rules) {
        return condition(true, 100, 80, false, rules);
    }

    private SearchCondition condition(
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled,
            SearchConditionRule... rules
    ) {
        SearchCondition condition = SearchCondition.create(
                "condition", null, enabled, priority,
                screeningScore, realtimeEnabled, null);
        for (SearchConditionRule rule : rules) {
            condition.addRule(rule);
        }
        return condition;
    }

    private SearchConditionRule valueRule(
            ScreeningStage stage,
            ScreeningMetric metric,
            Integer period
    ) {
        return SearchConditionRule.createValueRule(
                stage, metric, period,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ONE, ScreeningLogicalOperator.AND, 1);
    }

    private SearchConditionRule metricRule(
            ScreeningStage stage,
            ScreeningMetric leftMetric,
            Integer leftPeriod,
            ScreeningMetric rightMetric,
            Integer rightPeriod
    ) {
        return SearchConditionRule.createMetricRule(
                stage, leftMetric, leftPeriod,
                ScreeningOperator.GREATER_THAN,
                rightMetric, rightPeriod,
                ScreeningLogicalOperator.AND, 1);
    }
}
