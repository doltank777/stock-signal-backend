package com.stockapp.external.kis.master;

import java.util.List;

public record KisMasterReconciliationPlan(
        boolean kospiReady,
        boolean kosdaqReady,
        int totalMasterRecords,
        int supportedMasterRecords,
        int unsupportedMasterRecords,
        int targetStockCount,
        int existingMatchedCount,
        int existingUnsupportedCount,
        int estimatedUpdatedStockCount,
        int unchangedStockCount,
        int reappearedStockCount,
        int runningExecutionCount,
        List<KisMasterReconciliationCandidate> newSupportedStocks,
        List<KisMasterMissingStock> missingStocks,
        List<KisMasterIdentityConflict> identityConflicts
) {
    public KisMasterReconciliationPlan {
        newSupportedStocks = List.copyOf(newSupportedStocks);
        missingStocks = List.copyOf(missingStocks);
        identityConflicts = List.copyOf(identityConflicts);
    }

    public boolean ready() {
        return kospiReady && kosdaqReady;
    }

    public boolean applyAllowed() {
        return ready() && identityConflicts.isEmpty() && runningExecutionCount == 0;
    }
}
