package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningRightType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RealtimeSignalRequirementAnalyzer {

    public int requiredDailyPeriod(
            List<RealtimeSignalConditionDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions are required");
        }
        int requiredPeriod = 0;
        for (RealtimeSignalConditionDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException(
                        "definitions must not contain null");
            }
            for (RealtimeSignalRule rule : definition.rules()) {
                requiredPeriod = Math.max(requiredPeriod,
                        dailyPeriod(rule.leftMetric(), rule.leftPeriod()));
                if (rule.rightType() == ScreeningRightType.METRIC) {
                    requiredPeriod = Math.max(requiredPeriod,
                            dailyPeriod(rule.rightMetric(), rule.rightPeriod()));
                }
            }
        }
        return requiredPeriod;
    }

    private int dailyPeriod(ScreeningMetric metric, Integer period) {
        return switch (metric) {
            case AVERAGE_VOLUME, VOLUME_RATIO, MOVING_AVERAGE -> period;
            case CURRENT_PRICE, CHANGE_RATE, VOLUME -> 0;
        };
    }
}
