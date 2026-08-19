package com.stockapp.external.kis.probe;

import com.stockapp.external.kis.KisSubscriptionStatus;
import com.stockapp.external.kis.KisWebSocketSubscriptionResult;

import java.util.List;
import java.util.Optional;

public record KisWebSocketProbeSummary(
        List<String> requestedStockCodes,
        List<KisWebSocketSubscriptionResult> subscriptionResults
) {
    public KisWebSocketProbeSummary {
        requestedStockCodes = List.copyOf(requestedStockCodes);
        subscriptionResults = List.copyOf(subscriptionResults);
    }

    public long confirmedCount() {
        return count(KisSubscriptionStatus.CONFIRMED);
    }

    public long failedCount() {
        return count(KisSubscriptionStatus.FAILED);
    }

    public List<String> confirmedStockCodes() {
        return stockCodes(KisSubscriptionStatus.CONFIRMED);
    }

    public List<String> failedStockCodes() {
        return stockCodes(KisSubscriptionStatus.FAILED);
    }

    public Optional<KisWebSocketSubscriptionResult> firstFailure() {
        return subscriptionResults.stream()
                .filter(result -> result.status() == KisSubscriptionStatus.FAILED)
                .findFirst();
    }

    private long count(KisSubscriptionStatus status) {
        return subscriptionResults.stream()
                .filter(result -> result.status() == status)
                .count();
    }

    private List<String> stockCodes(KisSubscriptionStatus status) {
        return subscriptionResults.stream()
                .filter(result -> result.status() == status)
                .map(KisWebSocketSubscriptionResult::stockCode)
                .toList();
    }
}
