package com.stockapp.domain.stock;

import java.time.Instant;

public record KisMasterSyncExecutionCompletion(
        Instant observedAt,
        int kospiParsedRowCount,
        int kosdaqParsedRowCount,
        int supportedInstrumentCount,
        int unsupportedInstrumentCount,
        int unknownInstrumentCount,
        int duplicateShortCodeCount,
        int invalidRowCount
) {
    public KisMasterSyncExecutionCompletion {
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt must not be null");
        }
        if (kospiParsedRowCount < 0
                || kosdaqParsedRowCount < 0
                || supportedInstrumentCount < 0
                || unsupportedInstrumentCount < 0
                || unknownInstrumentCount < 0
                || duplicateShortCodeCount < 0
                || invalidRowCount < 0) {
            throw new IllegalArgumentException("Master sync counts must not be negative");
        }
        int total = kospiParsedRowCount + kosdaqParsedRowCount;
        if (supportedInstrumentCount + unsupportedInstrumentCount != total) {
            throw new IllegalArgumentException(
                    "supported and unsupported counts must equal total parsed count");
        }
    }

    public int totalParsedRowCount() {
        return kospiParsedRowCount + kosdaqParsedRowCount;
    }
}
