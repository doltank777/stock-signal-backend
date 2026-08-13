package com.stockapp.domain.stock.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class DailyPriceInitialLoadResult {
    private int targetStockCount;
    private int completedStockCount;
    private int skippedStockCount;
    private int partialHistoryStockCount;
    private int failedStockCount;
    private int requestedDailyPriceCount;
    private int savedDailyPriceCount;
    private int skippedDailyPriceCount;
    private int apiCallCount;
    private Instant startedAt;
    private Instant finishedAt;
    private List<DailyPriceLoadFailure> failures;
}
