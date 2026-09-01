package com.stockapp.domain.stock.dto;

import com.stockapp.domain.stock.Stock;

import java.util.List;
import java.util.Objects;

public record BootstrapDailyHistoryPlan(
        BootstrapDailyHistoryRequest request,
        List<Stock> targets,
        String universeFingerprint,
        String universePolicyVersion
) {
    public BootstrapDailyHistoryPlan {
        Objects.requireNonNull(request, "request is required");
        targets = List.copyOf(Objects.requireNonNull(targets,
                "targets are required"));
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }
        if (universeFingerprint == null || universeFingerprint.isBlank()
                || universePolicyVersion == null
                || universePolicyVersion.isBlank()) {
            throw new IllegalArgumentException("universe metadata is required");
        }
    }
}
