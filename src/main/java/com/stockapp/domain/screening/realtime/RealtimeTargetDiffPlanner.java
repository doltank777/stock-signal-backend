package com.stockapp.domain.screening.realtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class RealtimeTargetDiffPlanner {

    private static final Comparator<RealtimeWatchTarget> STOCK_CODE_ORDER =
            Comparator.comparing(RealtimeWatchTarget::stockCode);

    private final RealtimeWatchTargetMapper targetMapper;

    public RealtimeTargetDiffPlanner(RealtimeWatchTargetMapper targetMapper) {
        this.targetMapper = targetMapper;
    }

    public RealtimeTargetDiff plan(
            OperationalRealtimeTargetSelection desiredSelection,
            List<RealtimeWatchTarget> currentRegistryTargets,
            Set<String> currentPhysicalStockCodes
    ) {
        Objects.requireNonNull(desiredSelection,
                "desiredSelection is required");
        if (desiredSelection.selectedCount() > RealtimeWatchPolicy.CAPACITY) {
            throw new IllegalArgumentException(
                    "desired target count exceeds realtime capacity");
        }

        List<RealtimeWatchTarget> desiredTargets = targetMapper.mapAll(
                desiredSelection.selectedTargets());
        Map<String, RealtimeWatchTarget> desiredByCode = uniqueByStockCode(
                desiredTargets, "desired");
        Map<String, RealtimeWatchTarget> currentByCode = uniqueByStockCode(
                currentRegistryTargets, "current registry");
        Set<String> physicalCodes = validatePhysicalCodes(
                currentPhysicalStockCodes);
        validateStockIds(desiredByCode, currentByCode);

        List<String> toUnsubscribe = physicalCodes.stream()
                .filter(code -> !desiredByCode.containsKey(code))
                .sorted()
                .toList();
        List<RealtimeWatchTarget> toSubscribe = desiredTargets.stream()
                .filter(target -> !physicalCodes.contains(target.stockCode()))
                .toList();
        List<RealtimeWatchTarget> unchanged = new ArrayList<>();
        List<RealtimeWatchTarget> metadataChanged = new ArrayList<>();
        for (RealtimeWatchTarget desired : desiredTargets) {
            if (!physicalCodes.contains(desired.stockCode())) {
                continue;
            }
            RealtimeWatchTarget current = currentByCode.get(
                    desired.stockCode());
            if (current != null && sameMetadata(current, desired)) {
                unchanged.add(desired);
            } else {
                metadataChanged.add(desired);
            }
        }

        List<String> orphanPhysical = physicalCodes.stream()
                .filter(code -> !currentByCode.containsKey(code))
                .sorted()
                .toList();
        List<RealtimeWatchTarget> staleRegistry = currentByCode.values()
                .stream()
                .filter(target -> !physicalCodes.contains(target.stockCode()))
                .sorted(STOCK_CODE_ORDER)
                .toList();
        int expectedFinalCount = Math.addExact(
                Math.subtractExact(physicalCodes.size(), toUnsubscribe.size()),
                toSubscribe.size());
        boolean registryUpdateRequired = !sameRoutingSnapshot(
                currentByCode, desiredByCode);
        return new RealtimeTargetDiff(
                desiredTargets, unchanged, metadataChanged,
                toUnsubscribe, toSubscribe, orphanPhysical,
                staleRegistry, physicalCodes.size(), expectedFinalCount,
                registryUpdateRequired);
    }

    private Map<String, RealtimeWatchTarget> uniqueByStockCode(
            List<RealtimeWatchTarget> targets,
            String source
    ) {
        Objects.requireNonNull(targets, source + " targets are required");
        Map<String, RealtimeWatchTarget> targetsByCode = new LinkedHashMap<>();
        for (RealtimeWatchTarget target : targets) {
            Objects.requireNonNull(target, source + " target is required");
            if (targetsByCode.putIfAbsent(target.stockCode(), target) != null) {
                throw new IllegalArgumentException(
                        "duplicate " + source + " stockCode: "
                                + target.stockCode());
            }
        }
        return targetsByCode;
    }

    private Set<String> validatePhysicalCodes(Set<String> stockCodes) {
        Objects.requireNonNull(stockCodes,
                "currentPhysicalStockCodes are required");
        Set<String> copy = new LinkedHashSet<>();
        for (String stockCode : stockCodes) {
            Objects.requireNonNull(stockCode,
                    "current physical stockCode is required");
            if (stockCode.isBlank()) {
                throw new IllegalArgumentException(
                        "current physical stockCode must not be blank");
            }
            copy.add(stockCode);
        }
        return Set.copyOf(copy);
    }

    private void validateStockIds(
            Map<String, RealtimeWatchTarget> desiredByCode,
            Map<String, RealtimeWatchTarget> currentByCode
    ) {
        desiredByCode.forEach((stockCode, desired) -> {
            RealtimeWatchTarget current = currentByCode.get(stockCode);
            if (current != null
                    && !current.stockId().equals(desired.stockId())) {
                throw new IllegalArgumentException(
                        "same stockCode must have the same stockId: "
                                + stockCode);
            }
        });
    }

    private boolean sameMetadata(
            RealtimeWatchTarget current,
            RealtimeWatchTarget desired
    ) {
        return current.stockId().equals(desired.stockId())
                && new HashSet<>(current.conditionIds()).equals(
                new HashSet<>(desired.conditionIds()));
    }

    private boolean sameRoutingSnapshot(
            Map<String, RealtimeWatchTarget> currentByCode,
            Map<String, RealtimeWatchTarget> desiredByCode
    ) {
        if (!currentByCode.keySet().equals(desiredByCode.keySet())) {
            return false;
        }
        return desiredByCode.entrySet().stream().allMatch(entry ->
                sameMetadata(currentByCode.get(entry.getKey()),
                        entry.getValue()));
    }
}
