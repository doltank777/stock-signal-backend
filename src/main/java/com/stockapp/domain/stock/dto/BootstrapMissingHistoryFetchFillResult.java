package com.stockapp.domain.stock.dto;

import com.stockapp.domain.stock.BootstrapMissingHistoryFetchFillStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record BootstrapMissingHistoryFetchFillResult(
        BootstrapMissingHistoryFetchFillStatus status,
        String stockCode,
        int initialMissingCount,
        int plannedRangeCount,
        int plannedChunkCount,
        int attemptedChunkCount,
        int apiCallCount,
        int savedRowCount,
        int skippedRowCount,
        int emptyResponseChunkCount,
        int outOfRangeResponseRowCount,
        List<LocalDate> remainingMissingDates,
        BootstrapMissingHistoryFetchFillFailure failure
) {

    public BootstrapMissingHistoryFetchFillResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(stockCode, "stockCode is required");
        Objects.requireNonNull(remainingMissingDates,
                "remainingMissingDates is required");
        remainingMissingDates = List.copyOf(remainingMissingDates);
    }
}
