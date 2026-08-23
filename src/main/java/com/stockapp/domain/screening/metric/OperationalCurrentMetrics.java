package com.stockapp.domain.screening.metric;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record OperationalCurrentMetrics(
        BigDecimal currentPrice,
        Optional<BigDecimal> changeRate,
        BigDecimal volume
) {

    public OperationalCurrentMetrics {
        Objects.requireNonNull(currentPrice, "currentPrice is required");
        Objects.requireNonNull(changeRate, "changeRate Optional is required");
        Objects.requireNonNull(volume, "volume is required");
    }
}
