package com.stockapp.external.kis.dailypriceprobe;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Getter
@Setter
@ConfigurationProperties(prefix = "kis-daily-price-probe")
public class KisDailyPriceProbeProperties {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private String stockCode;
    private String targetDate;

    public String requiredStockCode() {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException(
                    "kis-daily-price-probe.stock-code is required");
        }
        return stockCode.trim();
    }

    public LocalDate resolvedTargetDate(Clock clock) {
        if (targetDate == null || targetDate.isBlank()) {
            return LocalDate.now(clock.withZone(KOREA_ZONE));
        }
        try {
            return LocalDate.parse(targetDate.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "kis-daily-price-probe.target-date must use yyyy-MM-dd: "
                            + targetDate, exception);
        }
    }
}
