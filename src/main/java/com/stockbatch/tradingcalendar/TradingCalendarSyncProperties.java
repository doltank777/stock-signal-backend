package com.stockbatch.tradingcalendar;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@ConfigurationProperties(prefix = "trading-calendar-sync")
public class TradingCalendarSyncProperties {

    private String startDate;
    private String endDate;

    public LocalDate resolvedStartDate() {
        return parseRequired("start-date", startDate);
    }

    public LocalDate resolvedEndDate(LocalDate resolvedStartDate) {
        LocalDate resolvedEndDate = parseRequired("end-date", endDate);
        if (resolvedEndDate.isBefore(resolvedStartDate)) {
            throw new IllegalArgumentException(
                    "trading-calendar-sync.end-date must be on or after start-date");
        }
        return resolvedEndDate;
    }

    private LocalDate parseRequired(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "trading-calendar-sync." + name + " is required");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "trading-calendar-sync." + name
                            + " must use yyyy-MM-dd: " + value,
                    exception);
        }
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
