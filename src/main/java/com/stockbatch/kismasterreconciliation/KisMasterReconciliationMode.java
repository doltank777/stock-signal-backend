package com.stockbatch.kismasterreconciliation;

import java.util.Locale;
import java.util.Optional;

public enum KisMasterReconciliationMode {
    DRY_RUN,
    APPLY;

    public static Optional<KisMasterReconciliationMode> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return Optional.of(valueOf(normalized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
