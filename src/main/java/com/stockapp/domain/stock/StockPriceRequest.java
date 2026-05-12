package com.stockapp.domain.stock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class StockPriceRequest {

    @NotBlank(message = "종목코드는 필수입니다.")
    private String stockCode;

    @NotNull(message = "현재가는 필수입니다.")
    private Long currentPrice;

    @NotNull(message = "등락률은 필수입니다.")
    private Double changeRate;

    @NotNull(message = "거래량은 필수입니다.")
    private Long volume;

    @NotNull(message = "거래일은 필수입니다.")
    private LocalDate tradeDate;
}