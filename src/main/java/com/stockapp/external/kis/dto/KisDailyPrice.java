package com.stockapp.external.kis.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class KisDailyPrice {

    private LocalDate tradeDate;
    private Long openPrice;
    private Long highPrice;
    private Long lowPrice;
    private Long closePrice;
    private Long volume;
}
