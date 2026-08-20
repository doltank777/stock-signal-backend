package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPriceFinalizationTargetDateResolverTest {

    @Test
    void resolvesCurrentKstDateWithoutAssumingPreviousTradingDay() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-19T15:30:00Z"), ZoneOffset.UTC);

        LocalDate result = new DailyPriceFinalizationTargetDateResolver(clock)
                .resolveScheduledTargetDate();

        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 20));
    }
}
