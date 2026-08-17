package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScreeningDataRequirementAnalyzer {

    public ScreeningDataRequirements analyze(SearchCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("condition is required");
        }
        return analyzeCondition(condition);
    }

    public ScreeningDataRequirements analyze(List<SearchCondition> conditions) {
        if (conditions == null) {
            throw new IllegalArgumentException("conditions are required");
        }

        boolean snapshotRequired = false;
        int maxDailyPeriod = 0;
        for (SearchCondition condition : conditions) {
            if (condition == null) {
                throw new IllegalArgumentException(
                        "conditions must not contain null");
            }
            ScreeningDataRequirements requirements = analyzeCondition(condition);
            snapshotRequired |= requirements.snapshotRequired();
            maxDailyPeriod = Math.max(
                    maxDailyPeriod, requirements.maxDailyPeriod());
        }
        return new ScreeningDataRequirements(
                snapshotRequired, maxDailyPeriod);
    }

    private ScreeningDataRequirements analyzeCondition(
            SearchCondition condition
    ) {
        MutableRequirements requirements = new MutableRequirements();
        int screeningRuleCount = 0;

        for (SearchConditionRule rule : condition.getRules()) {
            if (rule.getStage() != ScreeningStage.SCREENING) {
                continue;
            }

            screeningRuleCount++;
            includeMetric(
                    rule.getLeftMetric(), rule.getLeftPeriod(), requirements);

            if (rule.getRightType() == null) {
                throw new IllegalArgumentException("rightType is required");
            }
            if (rule.getRightType() == ScreeningRightType.METRIC) {
                includeMetric(
                        rule.getRightMetric(), rule.getRightPeriod(), requirements);
            }
        }

        if (screeningRuleCount == 0) {
            throw new IllegalArgumentException(
                    "at least one SCREENING rule is required");
        }
        return new ScreeningDataRequirements(
                requirements.snapshotRequired,
                requirements.maxDailyPeriod);
    }

    private void includeMetric(
            ScreeningMetric metric,
            Integer period,
            MutableRequirements requirements
    ) {
        if (metric == null) {
            throw new IllegalArgumentException("metric is required");
        }

        if (requiresSnapshot(metric)) {
            requirements.snapshotRequired = true;
        }
        if (requiresDaily(metric)) {
            if (period == null || period < 1) {
                throw new IllegalArgumentException(
                        metric + " requires a positive period");
            }
            requirements.maxDailyPeriod = Math.max(
                    requirements.maxDailyPeriod, period);
        }
    }

    private boolean requiresSnapshot(ScreeningMetric metric) {
        return switch (metric) {
            case CURRENT_PRICE, CHANGE_RATE, VOLUME, VOLUME_RATIO -> true;
            case AVERAGE_VOLUME, MOVING_AVERAGE -> false;
        };
    }

    private boolean requiresDaily(ScreeningMetric metric) {
        return switch (metric) {
            case AVERAGE_VOLUME, VOLUME_RATIO, MOVING_AVERAGE -> true;
            case CURRENT_PRICE, CHANGE_RATE, VOLUME -> false;
        };
    }

    private static class MutableRequirements {

        private boolean snapshotRequired;
        private int maxDailyPeriod;
    }
}
