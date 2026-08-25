package com.stockapp.domain.stock.dto;

public record BootstrapMissingHistoryFetchFillFailure(
        KisDailyPriceRequestChunk chunk,
        String exceptionType,
        String message,
        int attemptCount
) {
}
