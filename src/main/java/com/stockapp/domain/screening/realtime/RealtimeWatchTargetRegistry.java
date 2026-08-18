package com.stockapp.domain.screening.realtime;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RealtimeWatchTargetRegistry {

    private final AtomicReference<Map<String, RealtimeWatchTarget>> snapshot =
            new AtomicReference<>(Map.of());

    public void replace(List<RealtimeWatchTarget> targets) {
        Objects.requireNonNull(targets, "targets are required");

        Map<String, RealtimeWatchTarget> replacement = new LinkedHashMap<>();
        for (RealtimeWatchTarget target : targets) {
            Objects.requireNonNull(target, "target is required");
            RealtimeWatchTarget previous = replacement.putIfAbsent(
                    target.stockCode(), target);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate stockCode: " + target.stockCode());
            }
        }

        snapshot.set(Collections.unmodifiableMap(replacement));
    }

    public Optional<RealtimeWatchTarget> findByStockCode(String stockCode) {
        Objects.requireNonNull(stockCode, "stockCode is required");
        return Optional.ofNullable(snapshot.get().get(stockCode));
    }

    public Map<String, RealtimeWatchTarget> findAll() {
        return snapshot.get();
    }

    public int size() {
        return snapshot.get().size();
    }

    public boolean isEmpty() {
        return snapshot.get().isEmpty();
    }
}
