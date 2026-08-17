package com.stockapp.domain.screening.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ScreeningRunResult(
        LocalDate baseDate,
        Instant startedAt,
        Instant finishedAt,
        int totalStockCount,
        int evaluatedStockCount,
        List<ScreeningCandidate> candidates,
        List<ScreeningFailure> failures
) {

    public ScreeningRunResult {
        Objects.requireNonNull(baseDate, "baseDate is required");
        Objects.requireNonNull(startedAt, "startedAt is required");
        Objects.requireNonNull(finishedAt, "finishedAt is required");
        candidates = List.copyOf(
                Objects.requireNonNull(candidates, "candidates are required"));
        failures = List.copyOf(
                Objects.requireNonNull(failures, "failures are required"));

        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "finishedAt must not be before startedAt");
        }
        if (totalStockCount < 0) {
            throw new IllegalArgumentException(
                    "totalStockCount must not be negative");
        }
        if (evaluatedStockCount < 0) {
            throw new IllegalArgumentException(
                    "evaluatedStockCount must not be negative");
        }
        if (evaluatedStockCount + failures.size() != totalStockCount) {
            throw new IllegalArgumentException(
                    "evaluatedStockCount and failedStockCount must equal totalStockCount");
        }
        if (candidates.size() > evaluatedStockCount) {
            throw new IllegalArgumentException(
                    "candidateStockCount must not exceed evaluatedStockCount");
        }

        totalMatches(candidates);
    }

    public int candidateStockCount() {
        return candidates.size();
    }

    public int totalMatchCount() {
        return totalMatches(candidates);
    }

    public int failedStockCount() {
        return failures.size();
    }

    private static int totalMatches(List<ScreeningCandidate> candidates) {
        int total = 0;
        for (ScreeningCandidate candidate : candidates) {
            total = Math.addExact(total, candidate.matches().size());
        }
        return total;
    }
}
