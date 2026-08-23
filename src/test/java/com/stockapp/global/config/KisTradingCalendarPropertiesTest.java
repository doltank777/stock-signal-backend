package com.stockapp.global.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisTradingCalendarPropertiesTest {

    @Test
    void usesOfficialRealSmartSleepDefaultsAndOneRetry() {
        KisProperties.TradingCalendar calendar = configuredCalendar();

        calendar.validateConfigured();

        assertThat(calendar.getRequestIntervalMs()).isEqualTo(1000);
        assertThat(calendar.getMaxPages()).isEqualTo(50);
        assertThat(calendar.getRateLimitRetryDelayMs()).isEqualTo(61000);
        assertThat(calendar.getRateLimitRetryMaxAttempts()).isEqualTo(2);
    }

    @Test
    void rejectsUnsafePacingConfiguration() {
        KisProperties.TradingCalendar calendar = configuredCalendar();
        calendar.setRateLimitRetryMaxAttempts(3);

        assertThatThrownBy(calendar::validateConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 1 and 2");
    }

    @Test
    void rejectsNonPositiveMaxPages() {
        KisProperties.TradingCalendar calendar = configuredCalendar();
        calendar.setMaxPages(0);

        assertThatThrownBy(calendar::validateConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max pages must be >= 1");

        calendar.setMaxPages(-1);
        assertThatThrownBy(calendar::validateConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max pages must be >= 1");
    }

    private KisProperties.TradingCalendar configuredCalendar() {
        KisProperties.TradingCalendar calendar =
                new KisProperties.TradingCalendar();
        calendar.setBaseUrl("https://calendar.example.com");
        calendar.setAppKey("calendar-key");
        calendar.setAppSecret("calendar-secret");
        return calendar;
    }
}
