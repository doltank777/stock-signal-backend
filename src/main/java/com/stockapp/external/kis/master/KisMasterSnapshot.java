package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record KisMasterSnapshot(
        MarketType market,
        Instant observedAt,
        List<KisMasterNormalizedRecord> records,
        int rawParsedRowCount,
        int normalizedRowCount,
        int supportedInstrumentCount,
        int unsupportedInstrumentCount,
        KisMasterSnapshotValidationResult validation
) {
    public KisMasterSnapshot {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(observedAt, "observedAt");
        records = List.copyOf(records);
        Objects.requireNonNull(validation, "validation");
    }

    public boolean publishable() {
        return validation.ready();
    }
}
