package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningRightType;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OperationalScreeningDataRequirementAnalyzer {

    private final ScreeningDataRequirementAnalyzer screeningAnalyzer;

    public OperationalScreeningDataRequirements analyze(
            List<SearchCondition> conditions
    ) {
        ScreeningDataRequirements screeningRequirements =
                screeningAnalyzer.analyze(conditions);
        boolean changeRateRequired = conditions.stream()
                .flatMap(condition -> condition.getRules().stream())
                .filter(rule -> rule.getStage() == ScreeningStage.SCREENING)
                .anyMatch(this::usesChangeRate);
        return new OperationalScreeningDataRequirements(
                screeningRequirements.maxDailyPeriod(),
                changeRateRequired);
    }

    private boolean usesChangeRate(SearchConditionRule rule) {
        return rule.getLeftMetric() == ScreeningMetric.CHANGE_RATE
                || rule.getRightType() == ScreeningRightType.METRIC
                && rule.getRightMetric() == ScreeningMetric.CHANGE_RATE;
    }
}
