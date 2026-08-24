package com.stockapp.external.kis;

public class RealtimeSubscriptionCapacityExceededException
        extends IllegalStateException {

    public RealtimeSubscriptionCapacityExceededException(
            int capacity,
            int currentActiveCount,
            String stockCode
    ) {
        super("realtime subscription capacity exceeded - capacity: "
                + capacity + ", currentActiveCount: " + currentActiveCount
                + ", stockCode: " + stockCode);
    }
}
