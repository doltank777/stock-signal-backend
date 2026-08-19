package com.stockapp.external.kis;

public class KisWebSocketException extends RuntimeException {

    private final KisWebSocketSubscriptionResult subscriptionResult;

    public KisWebSocketException(String message, Throwable cause) {
        super(message, cause);
        this.subscriptionResult = null;
    }

    public KisWebSocketException(String message,
                                 KisWebSocketSubscriptionResult subscriptionResult) {
        super(message);
        this.subscriptionResult = subscriptionResult;
    }

    public KisWebSocketSubscriptionResult subscriptionResult() {
        return subscriptionResult;
    }
}
