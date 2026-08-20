package com.stockapp.domain.stock.dto;

public record DailyPriceFinalizationExecutionResult(
        DailyPriceFinalizationBatchResult batch,
        DailyPriceCompletenessResult completeness
) {
}
