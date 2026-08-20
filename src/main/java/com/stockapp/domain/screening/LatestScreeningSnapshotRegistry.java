package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningRunResult;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LatestScreeningSnapshotRegistry {

    private final AtomicReference<LatestScreeningSnapshot> snapshot =
            new AtomicReference<>();

    public void replace(ScreeningRunResult result) {
        snapshot.set(LatestScreeningSnapshot.from(result));
    }

    public Optional<LatestScreeningSnapshot> findLatest() {
        return Optional.ofNullable(snapshot.get());
    }
}
