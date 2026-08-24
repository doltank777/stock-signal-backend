package com.stockapp.external.kis;

public class RealtimeSubscriptionSessionUnavailableException
        extends IllegalStateException {

    public RealtimeSubscriptionSessionUnavailableException() {
        super("managed realtime WebSocket session is not open");
    }
}
