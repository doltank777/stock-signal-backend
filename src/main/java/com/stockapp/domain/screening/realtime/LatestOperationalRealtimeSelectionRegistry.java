package com.stockapp.domain.screening.realtime;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LatestOperationalRealtimeSelectionRegistry {

    private final AtomicReference<OperationalRealtimeTargetSelection> snapshot =
            new AtomicReference<>();

    public void replace(OperationalRealtimeTargetSelection selection) {
        snapshot.set(Objects.requireNonNull(selection, "selection is required"));
    }

    public Optional<OperationalRealtimeTargetSelection> findLatest() {
        return Optional.ofNullable(snapshot.get());
    }
}
