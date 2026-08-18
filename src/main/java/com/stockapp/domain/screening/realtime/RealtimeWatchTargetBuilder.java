package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.Stock;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RealtimeWatchTargetBuilder {

    public List<RealtimeWatchTarget> build(ScreeningRunResult result) {
        Objects.requireNonNull(result, "result is required");

        Map<String, MergedTarget> targetsByStockCode = new LinkedHashMap<>();
        for (ScreeningCandidate candidate : result.candidates()) {
            mergeCandidate(targetsByStockCode, candidate);
        }

        return targetsByStockCode.values().stream()
                .filter(target -> !target.conditionIds().isEmpty())
                .map(target -> new RealtimeWatchTarget(
                        target.stockId(),
                        target.stockCode(),
                        new ArrayList<>(target.conditionIds())))
                .toList();
    }

    private void mergeCandidate(
            Map<String, MergedTarget> targetsByStockCode,
            ScreeningCandidate candidate
    ) {
        Stock stock = candidate.stock();
        Long stockId = Objects.requireNonNull(
                stock.getId(), "candidate stockId is required");
        String stockCode = Objects.requireNonNull(
                stock.getStockCode(), "candidate stockCode is required");

        List<Long> realtimeConditionIds = candidate.matches().stream()
                .filter(ScreeningMatch::realtimeEnabled)
                .map(match -> Objects.requireNonNull(
                        match.condition().getId(),
                        "realtime conditionId is required"))
                .toList();
        if (realtimeConditionIds.isEmpty()) {
            return;
        }

        MergedTarget target = targetsByStockCode.computeIfAbsent(
                stockCode,
                ignored -> new MergedTarget(
                        stockId, stockCode, new LinkedHashSet<>()));
        if (!target.stockId().equals(stockId)) {
            throw new IllegalArgumentException(
                    "same stockCode must have the same stockId: " + stockCode);
        }
        target.conditionIds().addAll(realtimeConditionIds);
    }

    private record MergedTarget(
            Long stockId,
            String stockCode,
            LinkedHashSet<Long> conditionIds
    ) {
    }
}
