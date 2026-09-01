package com.stockapp.external.kis.master;

public record KisMasterParseSummary(
        int parsedRowCount,
        int duplicateShortCodeCount,
        int blankCodeCount,
        int invalidRowCount
) {
}
