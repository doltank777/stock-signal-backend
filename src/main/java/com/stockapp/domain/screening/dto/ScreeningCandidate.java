package com.stockapp.domain.screening.dto;

import com.stockapp.domain.stock.Stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ScreeningCandidate(
        Stock stock,
        LocalDate baseDate,
        List<ScreeningMatch> matches
) {

    public ScreeningCandidate {
        Objects.requireNonNull(stock, "stock is required");
        Objects.requireNonNull(baseDate, "baseDate is required");
        matches = List.copyOf(
                Objects.requireNonNull(matches, "matches are required"));
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one match is required");
        }
    }
}
