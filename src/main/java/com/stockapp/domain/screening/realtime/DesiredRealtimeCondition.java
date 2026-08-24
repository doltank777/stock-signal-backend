package com.stockapp.domain.screening.realtime;

import java.util.Objects;

public record DesiredRealtimeCondition(
        Long searchConditionId,
        String searchConditionName,
        int priority,
        int screeningScore
) {

    public DesiredRealtimeCondition {
        Objects.requireNonNull(searchConditionId,
                "searchConditionId is required");
        Objects.requireNonNull(searchConditionName,
                "searchConditionName is required");
        if (searchConditionName.isBlank()) {
            throw new IllegalArgumentException(
                    "searchConditionName must not be blank");
        }
    }
}
