package com.stockapp.external.kis.tradingcalendarprobe;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Getter
@Setter
@ConfigurationProperties(prefix = "kis-trading-calendar-probe")
public class KisTradingCalendarProbeProperties {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private String baseDate;
    private String endDate;
    private boolean printAllRows = false;
    private int maxPrintRows = 50;
    private boolean logPageSummary = true;

    public LocalDate resolvedBaseDate(Clock clock) {
        if (baseDate == null || baseDate.isBlank()) {
            return LocalDate.now(clock.withZone(KOREA_ZONE));
        }
        try {
            return LocalDate.parse(baseDate.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "kis-trading-calendar-probe.base-date must use yyyy-MM-dd: "
                            + baseDate, exception);
        }
    }

    public int validatedMaxPrintRows() {
        if (maxPrintRows < 0) {
            throw new IllegalArgumentException(
                    "kis-trading-calendar-probe.max-print-rows must be >= 0");
        }
        return maxPrintRows;
    }

    public LocalDate resolvedEndDate(LocalDate resolvedBaseDate) {
        if (endDate == null || endDate.isBlank()) {
            return null;
        }
        LocalDate resolved;
        try {
            resolved = LocalDate.parse(endDate.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "kis-trading-calendar-probe.end-date must use yyyy-MM-dd: "
                            + endDate, exception);
        }
        if (resolved.isBefore(resolvedBaseDate)) {
            throw new IllegalArgumentException(
                    "kis-trading-calendar-probe.end-date must be on or after base-date");
        }
        return resolved;
    }
}
