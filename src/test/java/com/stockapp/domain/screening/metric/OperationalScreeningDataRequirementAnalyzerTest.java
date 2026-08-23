package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalScreeningDataRequirementAnalyzerTest {

    private final OperationalScreeningDataRequirementAnalyzer analyzer =
            new OperationalScreeningDataRequirementAnalyzer(
                    new ScreeningDataRequirementAnalyzer());

    @Test
    void currentPriceOnlyRequiresNoPreviousRows() {
        assertThat(analyzer.analyze(List.of(condition(valueRule(
                ScreeningMetric.CURRENT_PRICE, null)))))
                .isEqualTo(new OperationalScreeningDataRequirements(0, false));
    }

    @Test
    void changeRateRequiresOnePreviousRowWithoutHistory() {
        OperationalScreeningDataRequirements requirements = analyzer.analyze(
                List.of(condition(valueRule(
                        ScreeningMetric.CHANGE_RATE, null))));

        assertThat(requirements)
                .isEqualTo(new OperationalScreeningDataRequirements(0, true));
        assertThat(requirements.requiredPreviousRowCount()).isEqualTo(1);
    }

    @Test
    void usesLargestHistoryPeriodAndDetectsRightSideChangeRate() {
        SearchConditionRule movingAverage = valueRule(
                ScreeningMetric.MOVING_AVERAGE, 60);
        SearchConditionRule changeRateRight =
                SearchConditionRule.createMetricRule(
                        ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null,
                        ScreeningOperator.GREATER_THAN,
                        ScreeningMetric.CHANGE_RATE, null,
                        ScreeningLogicalOperator.AND, 2);

        assertThat(analyzer.analyze(List.of(condition(
                movingAverage, changeRateRight))))
                .isEqualTo(new OperationalScreeningDataRequirements(60, true));
    }

    @Test
    void ignoresSignalStageChangeRate() {
        SearchConditionRule screening = valueRule(
                ScreeningMetric.VOLUME_RATIO, 20);
        SearchConditionRule signal = SearchConditionRule.createValueRule(
                ScreeningStage.SIGNAL,
                ScreeningMetric.CHANGE_RATE, null,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ZERO,
                ScreeningLogicalOperator.AND, 2);

        assertThat(analyzer.analyze(List.of(condition(screening, signal))))
                .isEqualTo(new OperationalScreeningDataRequirements(20, false));
    }

    private SearchCondition condition(SearchConditionRule... rules) {
        SearchCondition condition = SearchCondition.create(
                "condition", null, true, 100, 80, true, null);
        for (SearchConditionRule rule : rules) {
            condition.addRule(rule);
        }
        return condition;
    }

    private SearchConditionRule valueRule(
            ScreeningMetric metric, Integer period) {
        return SearchConditionRule.createValueRule(
                ScreeningStage.SCREENING,
                metric,
                period,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ZERO,
                null,
                1);
    }
}
