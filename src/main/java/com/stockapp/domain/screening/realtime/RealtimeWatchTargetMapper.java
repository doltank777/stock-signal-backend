package com.stockapp.domain.screening.realtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RealtimeWatchTargetMapper {

    public RealtimeWatchTarget map(DesiredRealtimeTarget desiredTarget) {
        Objects.requireNonNull(desiredTarget, "desiredTarget is required");
        List<Long> conditionIds = desiredTarget.matchedConditions().stream()
                .map(DesiredRealtimeCondition::searchConditionId)
                .distinct()
                .sorted()
                .toList();
        return new RealtimeWatchTarget(
                desiredTarget.stockId(), desiredTarget.stockCode(),
                conditionIds);
    }

    public List<RealtimeWatchTarget> mapAll(
            List<DesiredRealtimeTarget> desiredTargets
    ) {
        Objects.requireNonNull(desiredTargets, "desiredTargets are required");
        return desiredTargets.stream().map(this::map).toList();
    }
}
