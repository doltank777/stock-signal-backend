package com.stockapp.domain.screening.realtime;

import java.util.List;
import java.util.Objects;

public record RealtimeTargetDiff(
        List<RealtimeWatchTarget> desiredTargets,
        List<RealtimeWatchTarget> unchangedTargets,
        List<RealtimeWatchTarget> metadataChangedTargets,
        List<String> toUnsubscribeStockCodes,
        List<RealtimeWatchTarget> toSubscribeTargets,
        List<String> orphanPhysicalStockCodes,
        List<RealtimeWatchTarget> staleRegistryTargets,
        int currentPhysicalCount,
        int expectedFinalPhysicalCount,
        boolean registryUpdateRequired
) {

    public RealtimeTargetDiff {
        desiredTargets = copy(desiredTargets, "desiredTargets");
        unchangedTargets = copy(unchangedTargets, "unchangedTargets");
        metadataChangedTargets = copy(
                metadataChangedTargets, "metadataChangedTargets");
        toUnsubscribeStockCodes = copy(
                toUnsubscribeStockCodes, "toUnsubscribeStockCodes");
        toSubscribeTargets = copy(
                toSubscribeTargets, "toSubscribeTargets");
        orphanPhysicalStockCodes = copy(
                orphanPhysicalStockCodes, "orphanPhysicalStockCodes");
        staleRegistryTargets = copy(
                staleRegistryTargets, "staleRegistryTargets");
        if (currentPhysicalCount < 0 || expectedFinalPhysicalCount < 0) {
            throw new IllegalArgumentException(
                    "physical target counts must not be negative");
        }
        int calculatedFinalCount = Math.addExact(
                Math.subtractExact(
                        currentPhysicalCount,
                        toUnsubscribeStockCodes.size()),
                toSubscribeTargets.size());
        if (expectedFinalPhysicalCount != calculatedFinalCount
                || expectedFinalPhysicalCount != desiredTargets.size()) {
            throw new IllegalArgumentException(
                    "expected final physical count is inconsistent");
        }
    }

    public boolean requiresPhysicalChanges() {
        return !toUnsubscribeStockCodes.isEmpty()
                || !toSubscribeTargets.isEmpty();
    }

    public boolean requiresRegistryUpdate() {
        return registryUpdateRequired;
    }

    private static <T> List<T> copy(List<T> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(
                values, fieldName + " are required"));
    }
}
