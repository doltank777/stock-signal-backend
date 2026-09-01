package com.stockapp.external.kis.master;

import java.util.List;

public record KisMasterParseResult(
        List<KisMasterRawRecord> records,
        KisMasterParseSummary summary
) {
    public KisMasterParseResult {
        records = List.copyOf(records);
    }
}
