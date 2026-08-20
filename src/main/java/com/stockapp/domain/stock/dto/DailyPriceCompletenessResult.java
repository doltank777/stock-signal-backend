package com.stockapp.domain.stock.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyPriceCompletenessResult(
        LocalDate targetTradeDate,
        int targetStockCount,
        int presentRowCount,
        int missingStockCount,
        int failedStockCount,
        boolean ready,
        List<String> missingStockCodes,
        List<String> failedStockCodes,
        List<String> noDataStockCodes
) {
    public DailyPriceCompletenessResult {
        missingStockCodes = List.copyOf(missingStockCodes);
        failedStockCodes = List.copyOf(failedStockCodes);
        noDataStockCodes = List.copyOf(noDataStockCodes);
    }
}
