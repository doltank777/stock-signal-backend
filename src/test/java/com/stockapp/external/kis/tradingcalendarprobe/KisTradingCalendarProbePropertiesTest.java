package com.stockapp.external.kis.tradingcalendarprobe;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisTradingCalendarProbePropertiesTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void defaultsToCurrentKoreaDateAndSafeLoggingOptions() {
        KisTradingCalendarProbeProperties properties =
                new KisTradingCalendarProbeProperties();

        assertThat(properties.resolvedBaseDate(CLOCK))
                .isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(properties.isPrintAllRows()).isFalse();
        assertThat(properties.getMaxPrintRows()).isEqualTo(50);
        assertThat(properties.isLogPageSummary()).isTrue();
    }

    @Test
    void validatesExplicitDateAndPrintLimit() {
        KisTradingCalendarProbeProperties properties =
                new KisTradingCalendarProbeProperties();
        properties.setBaseDate("20260820");
        properties.setMaxPrintRows(-1);

        assertThatThrownBy(() -> properties.resolvedBaseDate(CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
        assertThatThrownBy(properties::validatedMaxPrintRows)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be >= 0");
    }

    @Test
    void resolvesAndValidatesOptionalEndDate() {
        KisTradingCalendarProbeProperties properties =
                new KisTradingCalendarProbeProperties();
        LocalDate baseDate = LocalDate.of(2026, 8, 20);

        assertThat(properties.resolvedEndDate(baseDate)).isNull();
        properties.setEndDate("2027-12-31");
        assertThat(properties.resolvedEndDate(baseDate))
                .isEqualTo(LocalDate.of(2027, 12, 31));
        properties.setEndDate("20260821");
        assertThatThrownBy(() -> properties.resolvedEndDate(baseDate))
                .hasMessageContaining("yyyy-MM-dd");
        properties.setEndDate("2026-08-19");
        assertThatThrownBy(() -> properties.resolvedEndDate(baseDate))
                .hasMessageContaining("on or after base-date");
    }
}
