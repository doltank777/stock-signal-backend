package com.stockapp.domain.stock.dto;

import java.time.LocalDate;

public record DailyPriceData(
        LocalDate tradeDate,
        Long closePrice,
        Long volume
) {
}
