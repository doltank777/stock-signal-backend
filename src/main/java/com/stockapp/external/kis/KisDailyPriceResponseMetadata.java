package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisDailyPrice;

import java.util.List;

public record KisDailyPriceResponseMetadata(
        int httpStatus,
        String returnCode,
        String messageCode,
        String message,
        List<KisDailyPrice> rows
) {
    public KisDailyPriceResponseMetadata {
        rows = List.copyOf(rows);
    }
}
