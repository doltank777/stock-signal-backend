package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.SearchCondition;

import java.util.Objects;

public record ScreeningMatch(
        SearchCondition condition,
        int screeningScore,
        int priority,
        boolean realtimeEnabled
) {

    public ScreeningMatch {
        Objects.requireNonNull(condition, "condition is required");
    }
}
