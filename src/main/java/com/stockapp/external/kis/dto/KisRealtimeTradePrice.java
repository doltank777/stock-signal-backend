package com.stockapp.external.kis.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class KisRealtimeTradePrice {

    private String stockCode;
    private long currentPrice;
    private long accumulatedVolume;
    private LocalDateTime tradeDateTime;
}