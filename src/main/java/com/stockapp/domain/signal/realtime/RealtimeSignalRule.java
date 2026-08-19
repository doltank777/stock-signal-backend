package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningRightType;

import java.math.BigDecimal;
import java.util.Objects;

public record RealtimeSignalRule(
        int ruleOrder,
        ScreeningLogicalOperator logicalOperator,
        ScreeningMetric leftMetric,
        Integer leftPeriod,
        ScreeningOperator operator,
        ScreeningRightType rightType,
        BigDecimal rightValue,
        ScreeningMetric rightMetric,
        Integer rightPeriod
) {
    public RealtimeSignalRule {
        Objects.requireNonNull(leftMetric, "leftMetric is required");
        Objects.requireNonNull(operator, "operator is required");
        Objects.requireNonNull(rightType, "rightType is required");
        validatePeriod(leftMetric, leftPeriod, "leftMetric");
        if (rightType == ScreeningRightType.VALUE) {
            Objects.requireNonNull(rightValue, "rightValue is required for VALUE rules");
            if (rightMetric != null || rightPeriod != null) {
                throw new IllegalArgumentException(
                        "rightMetric and rightPeriod are not allowed for VALUE rules");
            }
        } else {
            Objects.requireNonNull(rightMetric, "rightMetric is required for METRIC rules");
            if (rightValue != null) {
                throw new IllegalArgumentException(
                        "rightValue is not allowed for METRIC rules");
            }
            validatePeriod(rightMetric, rightPeriod, "rightMetric");
        }
    }

    private static void validatePeriod(
            ScreeningMetric metric, Integer period, String fieldName) {
        boolean required = switch (metric) {
            case AVERAGE_VOLUME, VOLUME_RATIO, MOVING_AVERAGE -> true;
            case CURRENT_PRICE, CHANGE_RATE, VOLUME -> false;
        };
        if (required && (period == null || period < 1)) {
            throw new IllegalArgumentException(
                    fieldName + " requires a positive period");
        }
        if (!required && period != null) {
            throw new IllegalArgumentException(
                    fieldName + " does not allow a period");
        }
    }
}
