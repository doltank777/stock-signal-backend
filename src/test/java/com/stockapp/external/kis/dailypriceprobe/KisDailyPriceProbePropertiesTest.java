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

    @Test
    void resolvesExplicitRangeMode() {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();
        properties.setStartDate("2026-01-02");
        properties.setEndDate("2026-08-24");

        KisDailyPriceProbeRequest request = properties.resolvedRequest(CLOCK);

        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(request.singleDateMode()).isFalse();
    }

    @Test
    void preservesDefaultTodaySingleDateMode() {
        KisDailyPriceProbeRequest request =
                new KisDailyPriceProbeProperties().resolvedRequest(CLOCK);

        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(request.targetDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void rejectsTargetDateCombinedWithRange() {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();
        properties.setTargetDate("2026-08-20");
        properties.setStartDate("2026-08-01");
        properties.setEndDate("2026-08-24");

        assertThatThrownBy(() -> properties.resolvedRequest(CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be combined");
    }

    @Test
    void rejectsPartialRange() {
        KisDailyPriceProbeProperties startOnly = new KisDailyPriceProbeProperties();
        startOnly.setStartDate("2026-08-01");
        KisDailyPriceProbeProperties endOnly = new KisDailyPriceProbeProperties();
        endOnly.setEndDate("2026-08-24");

        assertThatThrownBy(() -> startOnly.resolvedRequest(CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
        assertThatThrownBy(() -> endOnly.resolvedRequest(CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
    }

    @Test
    void rejectsReversedRange() {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();
        properties.setStartDate("2026-08-24");
        properties.setEndDate("2026-08-01");

        assertThatThrownBy(() -> properties.resolvedRequest(CLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on or before");
    }
}
