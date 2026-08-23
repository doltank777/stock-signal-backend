package com.stockbatch.tradingcalendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradingCalendarSyncPropertiesTest {

    @Test
    void resolvesExplicitRange() {
        TradingCalendarSyncProperties properties = properties(
                "2026-01-01", "2027-12-31");

        LocalDate startDate = properties.resolvedStartDate();

        assertThat(startDate).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(properties.resolvedEndDate(startDate))
                .isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    void rejectsMissingInvalidAndReversedRange() {
        TradingCalendarSyncProperties properties = properties(null, null);
        assertThatThrownBy(properties::resolvedStartDate)
                .hasMessageContaining("start-date is required");

        properties = properties("20260101", "2027-12-31");
        assertThatThrownBy(properties::resolvedStartDate)
                .hasMessageContaining("yyyy-MM-dd");

        properties = properties("2026-01-02", "2026-01-01");
        LocalDate startDate = properties.resolvedStartDate();
        TradingCalendarSyncProperties reversed = properties;
        assertThatThrownBy(() -> reversed.resolvedEndDate(startDate))
                .hasMessageContaining("on or after start-date");
    }

    private TradingCalendarSyncProperties properties(
            String startDate,
            String endDate
    ) {
        TradingCalendarSyncProperties properties =
                new TradingCalendarSyncProperties();
        properties.setStartDate(startDate);
        properties.setEndDate(endDate);
        return properties;
    }
}
