package com.stockapp.external.kis.probe;

import com.stockapp.external.kis.KisWebSocketSubscriptionResult;

import java.util.Optional;

public record KisWebSocketProbeExecution(
        KisWebSocketProbeSummary initialSummary,
        KisWebSocketSubscriptionResult unsubscribeResult,
        KisWebSocketSubscriptionResult replacementResult,
        int activeCountAfterUnsubscribe,
        int activeCountAfterReplacement,
        KisWebSocketProbeSummary sessionBSummary,
        boolean sessionAOpen,
        boolean sessionBOpen,
        boolean bothOpen
) {
    public KisWebSocketProbeExecution(
            KisWebSocketProbeSummary initialSummary,
            KisWebSocketSubscriptionResult unsubscribeResult,
            KisWebSocketSubscriptionResult replacementResult,
            int activeCountAfterUnsubscribe,
            int activeCountAfterReplacement
    ) {
        this(initialSummary, unsubscribeResult, replacementResult,
                activeCountAfterUnsubscribe, activeCountAfterReplacement,
                null, false, false, false);
    }

    public Optional<KisWebSocketSubscriptionResult> optionalUnsubscribeResult() {
        return Optional.ofNullable(unsubscribeResult);
    }

    public Optional<KisWebSocketSubscriptionResult> optionalReplacementResult() {
        return Optional.ofNullable(replacementResult);
    }
}
