package com.stockapp.external.kis;

public class KisTradingCalendarRangeIncompleteException
        extends IllegalArgumentException {

    private final KisTradingCalendarFetchResult partialFetchResult;

    public KisTradingCalendarRangeIncompleteException(
            String message,
            KisTradingCalendarFetchResult partialFetchResult
    ) {
        super(message);
        this.partialFetchResult = partialFetchResult;
    }

    public KisTradingCalendarFetchResult getPartialFetchResult() {
        return partialFetchResult;
    }
}
