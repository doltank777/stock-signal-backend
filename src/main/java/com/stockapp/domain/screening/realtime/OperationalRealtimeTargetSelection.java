package com.stockapp.domain.screening.realtime;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record OperationalRealtimeTargetSelection(
        int capacity,
        int uniqueCandidateCount,
        List<DesiredRealtimeTarget> selectedTargets,
        List<DesiredRealtimeTarget> excludedTargets
) {

    public static OperationalRealtimeTargetSelection empty() {
        return new OperationalRealtimeTargetSelection(
                RealtimeWatchPolicy.CAPACITY, 0, List.of(), List.of());
    }

    public OperationalRealtimeTargetSelection {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        selectedTargets = List.copyOf(Objects.requireNonNull(
                selectedTargets, "selectedTargets are required"));
        excludedTargets = List.copyOf(Objects.requireNonNull(
                excludedTargets, "excludedTargets are required"));
        if (selectedTargets.size() > capacity) {
            throw new IllegalArgumentException(
                    "selected target count exceeds capacity");
        }
        Set<Long> stockIds = new HashSet<>();
        validateUniqueStocks(selectedTargets, stockIds);
        validateUniqueStocks(excludedTargets, stockIds);
        if (uniqueCandidateCount != stockIds.size()) {
            throw new IllegalArgumentException(
                    "uniqueCandidateCount does not match targets");
        }
    }

    public int selectedCount() {
        return selectedTargets.size();
    }

    public int excludedCount() {
        return excludedTargets.size();
    }

    private static void validateUniqueStocks(
            List<DesiredRealtimeTarget> targets,
            Set<Long> stockIds
    ) {
        for (DesiredRealtimeTarget target : targets) {
            Objects.requireNonNull(target, "target is required");
            if (!stockIds.add(target.stockId())) {
                throw new IllegalArgumentException(
                        "duplicate stockId: " + target.stockId());
            }
        }
    }
}
