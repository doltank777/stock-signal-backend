package com.stockapp.external.kis.dailypriceprobe;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisDailyPriceProbePropertiesTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void requiresStockCode() {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();

        assertThatThrownBy(properties::requiredStockCode)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kis-daily-price-probe.stock-code is required");
    }

    @Test
    void parsesExplicitTargetDate() {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();
        properties.setTargetDate("2026-08-19");

        assertThat(properties.resolvedTargetDate(CLOCK))
                .isEqualTo(LocalDate.of(2026, 8, 19));
    }

    @Test
    void defaultsTargetDateToTodayInKorea() {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();

        assertThat(properties.resolvedTargetDate(CLOCK))
                .isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void rejectsInvalidTargetDateBeforeApiCall() {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();
        properties.setTargetDate("20260820");

        assertThatThrownBy(() -> properties.resolvedTargetDate(CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }
}
