package com.stockapp.domain.stock.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockDailyPriceSaveResult {

    private int requestedCount;
    private int savedCount;
    private int skippedCount;
}
