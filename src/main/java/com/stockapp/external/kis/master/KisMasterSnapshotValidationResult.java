package com.stockapp.external.kis.master;

import java.util.List;
import java.util.Set;

public record KisMasterSnapshotValidationResult(
        KisMasterSnapshotValidationStatus status,
        List<String> errors,
        List<String> warnings,
        int parsedRowCount,
        int normalizedRowCount,
        int supportedInstrumentCount,
        int unsupportedInstrumentCount,
        int unknownInstrumentCount,
        int duplicateShortCodeCount,
        int duplicateStandardCodeCount,
        Set<String> unknownSecurityGroupCodes
) {
    public KisMasterSnapshotValidationResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
        unknownSecurityGroupCodes = Set.copyOf(unknownSecurityGroupCodes);
    }

    public boolean ready() {
        return status == KisMasterSnapshotValidationStatus.READY;
    }
}
