package com.stockapp.domain.signal.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class RealtimeSignalConditionDefinitionCache {

    private final RealtimeSignalConditionDefinitionLoader loader;
    private final ConcurrentMap<Long, RealtimeSignalConditionDefinition> entries =
            new ConcurrentHashMap<>();

    public Map<Long, RealtimeSignalConditionDefinition> getAll(
            List<Long> conditionIds) {
        Objects.requireNonNull(conditionIds, "conditionIds are required");
        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        for (Long conditionId : conditionIds) {
            requestedIds.add(Objects.requireNonNull(
                    conditionId, "conditionId is required"));
        }
        if (requestedIds.isEmpty()) {
            throw new IllegalArgumentException("conditionIds must not be empty");
        }

        loadMissing(requestedIds);

        Map<Long, RealtimeSignalConditionDefinition> result = new LinkedHashMap<>();
        for (Long conditionId : requestedIds) {
            RealtimeSignalConditionDefinition definition = entries.get(conditionId);
            if (definition == null) {
                throw new IllegalStateException(
                        "realtime SIGNAL condition definition is missing: " + conditionId);
            }
            result.put(conditionId, definition);
        }
        return Map.copyOf(result);
    }

    private synchronized void loadMissing(LinkedHashSet<Long> requestedIds) {
        List<Long> missingIds = requestedIds.stream()
                .filter(conditionId -> !entries.containsKey(conditionId))
                .toList();
        if (missingIds.isEmpty()) return;

        List<RealtimeSignalConditionDefinition> loaded = loader.load(missingIds);
        for (RealtimeSignalConditionDefinition definition : loaded) {
            if (!missingIds.contains(definition.conditionId())) {
                throw new IllegalStateException(
                        "loader returned an unrequested condition definition");
            }
            entries.putIfAbsent(definition.conditionId(), definition);
        }
    }
}
