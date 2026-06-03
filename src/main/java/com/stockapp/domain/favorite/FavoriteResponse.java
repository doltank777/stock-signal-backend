package com.stockapp.domain.favorite;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FavoriteResponse {

    private Long id;
    private String stockCode;
    private String stockName;
    private String marketType;

    public static FavoriteResponse from(Favorite favorite) {
        return FavoriteResponse.builder()
                .id(favorite.getId())
                .stockCode(favorite.getStock().getStockCode())
                .stockName(favorite.getStock().getStockName())
                .marketType(favorite.getStock().getMarketType().name())
                .build();
    }
}