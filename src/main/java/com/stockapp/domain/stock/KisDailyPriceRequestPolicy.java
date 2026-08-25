package com.stockapp.domain.stock;

public record KisDailyPriceRequestPolicy(
        int maxAttempts,
        long initialRetryDelayMs,
        long backoffMultiplier
) {
    public KisDailyPriceRequestPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        if (initialRetryDelayMs < 0) throw new IllegalArgumentException("initialRetryDelayMs must not be negative");
        if (backoffMultiplier < 1) throw new IllegalArgumentException("backoffMultiplier must be positive");
        long delay = initialRetryDelayMs;
        try {
            for (int attempt = 1; attempt < maxAttempts - 1; attempt++) {
                delay = Math.multiplyExact(delay, backoffMultiplier);
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("retry backoff exceeds long range", exception);
        }
    }
}
