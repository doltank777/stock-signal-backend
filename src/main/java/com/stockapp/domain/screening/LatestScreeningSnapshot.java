package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.MarketType;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record LatestScreeningSnapshot(
        LocalDate baseDate,
        List<Candidate> candidates
) {

    public LatestScreeningSnapshot {
        Objects.requireNonNull(baseDate, "baseDate is required");
        candidates = List.copyOf(
                Objects.requireNonNull(candidates, "candidates are required"));
    }

    public static LatestScreeningSnapshot from(ScreeningRunResult result) {
        Objects.requireNonNull(result, "result is required");
        return new LatestScreeningSnapshot(
                result.baseDate(),
                result.candidates().stream().map(Candidate::from).toList());
    }

    public record Candidate(
            Long stockId,
            String stockCode,
            String stockName,
            MarketType market,
            List<Match> matches
    ) {
        public Candidate {
            Objects.requireNonNull(stockId, "stockId is required");
            Objects.requireNonNull(stockCode, "stockCode is required");
            Objects.requireNonNull(stockName, "stockName is required");
            Objects.requireNonNull(market, "market is required");
            matches = List.copyOf(
                    Objects.requireNonNull(matches, "matches are required"));
        }

        private static Candidate from(ScreeningCandidate candidate) {
            return new Candidate(
                    candidate.stock().getId(),
                    candidate.stock().getStockCode(),
                    candidate.stock().getStockName(),
                    candidate.stock().getMarketType(),
                    candidate.matches().stream().map(Match::from).toList());
        }
    }

    public record Match(
            Long searchConditionId,
            String searchConditionName,
            int priority,
            boolean realtimeEnabled
    ) {
        public Match {
            Objects.requireNonNull(searchConditionId,
                    "searchConditionId is required");
            Objects.requireNonNull(searchConditionName,
                    "searchConditionName is required");
        }

        private static Match from(ScreeningMatch match) {
            return new Match(
                    match.condition().getId(),
                    match.condition().getName(),
                    match.priority(),
                    match.realtimeEnabled());
        }
    }
}
