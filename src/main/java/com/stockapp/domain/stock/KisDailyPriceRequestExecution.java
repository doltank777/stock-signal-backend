package com.stockapp.domain.stock;

public record KisDailyPriceRequestExecution<T>(T value, int attemptCount) {
}
