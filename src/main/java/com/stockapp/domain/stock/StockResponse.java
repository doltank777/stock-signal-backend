package com.stockapp.domain.stock;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockResponse {

    private Long id;
    private String stockCode;
    private String stockName;
    private MarketType marketType;

    public static StockResponse from(Stock stock) {
        return StockResponse.builder()
                .id(stock.getId())
                .stockCode(stock.getStockCode())
                .stockName(stock.getStockName())
                .marketType(stock.getMarketType())
                .build();
    }
}