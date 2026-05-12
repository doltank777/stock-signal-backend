package com.stockapp.domain.stock;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class StockPriceResponse {

    private Long id;
    private String stockCode;
    private Long currentPrice;
    private Double changeRate;
    private Long volume;
    private LocalDate tradeDate;
    private LocalDateTime collectedAt;

    public static StockPriceResponse from(StockPrice stockPrice) {
        return StockPriceResponse.builder()
                .id(stockPrice.getId())
                .stockCode(stockPrice.getStockCode())
                .currentPrice(stockPrice.getCurrentPrice())
                .changeRate(stockPrice.getChangeRate())
                .volume(stockPrice.getVolume())
                .tradeDate(stockPrice.getTradeDate())
                .collectedAt(stockPrice.getCollectedAt())
                .build();
    }
}