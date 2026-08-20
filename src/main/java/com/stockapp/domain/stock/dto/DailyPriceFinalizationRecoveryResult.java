package com.stockapp.domain.stock.dto;

public record DailyPriceFinalizationRecoveryResult(
        boolean alreadyReady,
        DailyPriceFinalizationExecutionSnapshot execution
) {
}
