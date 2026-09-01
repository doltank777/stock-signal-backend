package com.stockapp.external.kis.master;

public record KisMasterIdentityConflict(
        String masterStockCode,
        String masterStandardCode,
        Long existingStockId,
        String existingStockCode,
        String existingStandardCode
) {
}
