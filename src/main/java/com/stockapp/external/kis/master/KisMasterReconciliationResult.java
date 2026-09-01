package com.stockapp.external.kis.master;

public record KisMasterReconciliationResult(
        int totalMasterRecords,
        int supportedMasterRecords,
        int existingMatchedCount,
        int newStockCount,
        int updatedStockCount,
        int unchangedStockCount,
        int missingStockCount,
        int reappearedStockCount,
        int unsupportedMasterCount,
        int identityConflictCount,
        int historyCreatedCount
) {
}
