package com.stockapp.domain.stock.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class DailyPriceUpdateResult {

    private LocalDate baseDate;
    private int targetStockCount;
    private int updatedStockCount;
    private int upToDateStockCount;
    private int noNewDataStockCount;
    private int noBaseHistoryStockCount;
    private int failedStockCount;
    private int apiCallCount;
    private int savedDailyPriceCount;
    private Instant startedAt;
    private Instant finishedAt;
    private List<DailyPriceLoadFailure> failures;
}
