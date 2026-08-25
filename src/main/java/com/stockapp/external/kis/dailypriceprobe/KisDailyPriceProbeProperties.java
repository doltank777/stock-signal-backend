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
    private String startDate;
    private String endDate;

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

    public KisDailyPriceProbeRequest resolvedRequest(Clock clock) {
        boolean hasTargetDate = hasText(targetDate);
        boolean hasStartDate = hasText(startDate);
        boolean hasEndDate = hasText(endDate);

        if (hasTargetDate && (hasStartDate || hasEndDate)) {
            throw new IllegalArgumentException(
                    "target-date cannot be combined with start-date or end-date");
        }
        if (hasStartDate != hasEndDate) {
            throw new IllegalArgumentException(
                    "start-date and end-date must be provided together");
        }
        if (hasStartDate) {
            LocalDate resolvedStartDate = parseDate("start-date", startDate);
            LocalDate resolvedEndDate = parseDate("end-date", endDate);
            if (resolvedStartDate.isAfter(resolvedEndDate)) {
                throw new IllegalArgumentException(
                        "kis-daily-price-probe.start-date must be on or before end-date");
            }
            return new KisDailyPriceProbeRequest(
                    resolvedStartDate, resolvedEndDate, null);
        }

        LocalDate resolvedTargetDate = resolvedTargetDate(clock);
        return new KisDailyPriceProbeRequest(
                resolvedTargetDate, resolvedTargetDate, resolvedTargetDate);
    }

    private LocalDate parseDate(String propertyName, String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "kis-daily-price-probe." + propertyName
                            + " must use yyyy-MM-dd: " + value,
                    exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
