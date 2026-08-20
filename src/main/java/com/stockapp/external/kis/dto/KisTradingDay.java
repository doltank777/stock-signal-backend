package com.stockapp.external.kis.dto;

import java.time.LocalDate;

public record KisTradingDay(LocalDate tradeDate, boolean tradingDay) {
}
