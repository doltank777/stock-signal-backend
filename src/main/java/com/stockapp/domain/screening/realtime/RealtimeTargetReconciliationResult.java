package com.stockapp.domain.screening.realtime;

import com.stockapp.external.kis.RealtimeSubscriptionCommandOperation;

import java.util.List;
import java.util.Objects;

public record RealtimeTargetReconciliationResult(
        RealtimeTargetReconciliationStatus status,
        int desiredCount,
        int beforePhysicalCount,
        int afterPhysicalCount,
        List<String> unsubscribeRequestedStockCodes,
        List<String> unsubscribeAppliedStockCodes,
        List<String> subscribeRequestedStockCodes,
        List<String> subscribeAppliedStockCodes,
        int registryCount,
        RealtimeSubscriptionCommandOperation failedOperation,
        String failedStockCode,
        String failureType,
        String failureMessage,
        List<String> unmappedPhysicalStockCodes
) {

    public RealtimeTargetReconciliationResult {
        Objects.requireNonNull(status, "status is required");
        unsubscribeRequestedStockCodes = copy(
                unsubscribeRequestedStockCodes,
                "unsubscribeRequestedStockCodes");
        unsubscribeAppliedStockCodes = copy(
                unsubscribeAppliedStockCodes,
                "unsubscribeAppliedStockCodes");
        subscribeRequestedStockCodes = copy(
                subscribeRequestedStockCodes,
                "subscribeRequestedStockCodes");
        subscribeAppliedStockCodes = copy(
                subscribeAppliedStockCodes,
                "subscribeAppliedStockCodes");
        unmappedPhysicalStockCodes = copy(
                unmappedPhysicalStockCodes,
                "unmappedPhysicalStockCodes");
        if (desiredCount < 0 || beforePhysicalCount < 0
                || afterPhysicalCount < 0 || registryCount < 0) {
            throw new IllegalArgumentException(
                    "reconciliation counts must not be negative");
        }
        boolean hasFailure = failedOperation != null;
        if (hasFailure != (failedStockCode != null)
                || hasFailure != (failureType != null)) {
            throw new IllegalArgumentException(
                    "failed operation, stockCode, and type must be provided together");
        }
    }

    private static List<String> copy(List<String> values, String fieldName) {
        return List.copyOf(Objects.requireNonNull(
                values, fieldName + " are required"));
    }
}
