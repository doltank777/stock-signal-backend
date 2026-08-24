package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.Stock;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class OperationalRealtimeTargetSelector {

    private static final Comparator<DesiredRealtimeCondition> CONDITION_ORDER =
            Comparator.comparingInt(DesiredRealtimeCondition::priority)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(
                            DesiredRealtimeCondition::screeningScore)
                            .reversed())
                    .thenComparing(
                            DesiredRealtimeCondition::searchConditionId);
    private static final Comparator<DesiredRealtimeTarget> TARGET_ORDER =
            Comparator.comparingInt(DesiredRealtimeTarget::effectivePriority)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(
                            DesiredRealtimeTarget::effectiveScreeningScore)
                            .reversed())
                    .thenComparing(DesiredRealtimeTarget::stockCode)
                    .thenComparing(DesiredRealtimeTarget::stockId);

    public OperationalRealtimeTargetSelection select(
            ScreeningRunResult screeningResult
    ) {
        Objects.requireNonNull(screeningResult,
                "screeningResult is required");

        Map<String, MergedTarget> mergedByStockCode = new LinkedHashMap<>();
        for (ScreeningCandidate candidate : screeningResult.candidates()) {
            mergeCandidate(mergedByStockCode, candidate);
        }

        List<DesiredRealtimeTarget> rankedTargets = mergedByStockCode.values()
                .stream()
                .filter(target -> !target.conditionsById().isEmpty())
                .map(this::toDesiredTarget)
                .sorted(TARGET_ORDER)
                .toList();
        int selectedCount = Math.min(
                RealtimeWatchPolicy.CAPACITY, rankedTargets.size());
        return new OperationalRealtimeTargetSelection(
                RealtimeWatchPolicy.CAPACITY,
                rankedTargets.size(),
                rankedTargets.subList(0, selectedCount),
                rankedTargets.subList(selectedCount, rankedTargets.size()));
    }

    private void mergeCandidate(
            Map<String, MergedTarget> mergedByStockCode,
            ScreeningCandidate candidate
    ) {
        Stock stock = candidate.stock();
        Long stockId = Objects.requireNonNull(
                stock.getId(), "candidate stockId is required");
        String stockCode = Objects.requireNonNull(
                stock.getStockCode(), "candidate stockCode is required");
        MergedTarget target = mergedByStockCode.computeIfAbsent(
                stockCode,
                ignored -> new MergedTarget(
                        stockId, stockCode,
                        Objects.requireNonNull(stock.getStockName(),
                                "candidate stockName is required"),
                        Objects.requireNonNull(stock.getMarketType(),
                                "candidate market is required"),
                        new LinkedHashMap<>()));
        target.requireSameStock(stock);
        for (ScreeningMatch match : candidate.matches()) {
            if (match.realtimeEnabled()) {
                target.add(toCondition(match));
            }
        }
    }

    private DesiredRealtimeCondition toCondition(ScreeningMatch match) {
        return new DesiredRealtimeCondition(
                Objects.requireNonNull(match.condition().getId(),
                        "realtime conditionId is required"),
                match.condition().getName(),
                match.priority(),
                match.screeningScore());
    }

    private DesiredRealtimeTarget toDesiredTarget(MergedTarget target) {
        List<DesiredRealtimeCondition> conditions =
                target.conditionsById().values().stream()
                        .sorted(CONDITION_ORDER)
                        .toList();
        DesiredRealtimeCondition best = conditions.getFirst();
        return new DesiredRealtimeTarget(
                target.stockId(), target.stockCode(), target.stockName(),
                target.market(), best.priority(), best.screeningScore(),
                conditions);
    }

    private record MergedTarget(
            Long stockId,
            String stockCode,
            String stockName,
            com.stockapp.domain.stock.MarketType market,
            Map<Long, DesiredRealtimeCondition> conditionsById
    ) {

        private void requireSameStock(Stock stock) {
            if (!stockId.equals(stock.getId())
                    || !stockName.equals(stock.getStockName())
                    || market != stock.getMarketType()) {
                throw new IllegalArgumentException(
                        "same stockCode must have consistent stock metadata: "
                                + stockCode);
            }
        }

        private void add(DesiredRealtimeCondition condition) {
            DesiredRealtimeCondition existing = conditionsById.putIfAbsent(
                    condition.searchConditionId(), condition);
            if (existing != null && !existing.equals(condition)) {
                throw new IllegalArgumentException(
                        "same searchConditionId must have consistent metadata: "
                                + condition.searchConditionId());
            }
        }
    }
}
