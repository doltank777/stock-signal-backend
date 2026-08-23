package com.stockapp.external.kis;

import lombok.Getter;

@Getter
public class KisTradingCalendarPaginationLimitException
        extends IllegalArgumentException {

    private final int maxPages;
    private final int completedPages;
    private final int apiCallCount;
    private final KisTradingCalendarFetchResult partialFetchResult;

    public KisTradingCalendarPaginationLimitException(
            int maxPages,
            KisTradingCalendarFetchResult partialFetchResult
    ) {
        this(maxPages, partialFetchResult.pages().size(),
                partialFetchResult.apiCallCount(), partialFetchResult);
    }

    private KisTradingCalendarPaginationLimitException(
            int maxPages,
            int completedPages,
            int apiCallCount,
            KisTradingCalendarFetchResult partialFetchResult
    ) {
        super("KIS trading calendar pagination exceeded max pages: maxPages="
                + maxPages + ", completedPages=" + completedPages
                + ", apiCallCount=" + apiCallCount
                + ", collectedRowCount=" + partialFetchResult.days().size());
        this.maxPages = maxPages;
        this.completedPages = completedPages;
        this.apiCallCount = apiCallCount;
        this.partialFetchResult = partialFetchResult;
    }
}
