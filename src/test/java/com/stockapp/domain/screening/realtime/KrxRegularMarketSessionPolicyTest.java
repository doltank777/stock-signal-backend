package com.stockapp.domain.screening.realtime;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrxRegularMarketSessionPolicyTest {

    @Test
    void startupRecoveryUsesInclusiveMorningStartAndMarketClose() {
        assertThat(policyAt("2026-08-24T08:29:59")
                .isStartupRecoveryWindow()).isFalse();
        assertThat(policyAt("2026-08-24T08:30:00")
                .isStartupRecoveryWindow()).isTrue();
        assertThat(policyAt("2026-08-24T09:00:00")
                .isStartupRecoveryWindow()).isTrue();
        assertThat(policyAt("2026-08-24T15:30:00")
                .isStartupRecoveryWindow()).isTrue();
        assertThat(policyAt("2026-08-24T15:30:01")
                .isStartupRecoveryWindow()).isFalse();
        assertThat(policyAt("2026-08-24T23:00:00")
                .isStartupRecoveryWindow()).isFalse();
    }

    @Test
    void deadlineIsInclusiveAndRegularMarketBoundariesAreInclusive() {
        var policy = policyAt("2026-08-24T08:55:00");
        assertThat(policy.isMorningPreparationWindow(policy.now())).isTrue();
        assertThat(policy.isMorningDeadlineReached(policy.now())).isTrue();
        var open = policyAt("2026-08-24T09:00:00");
        var close = policyAt("2026-08-24T15:30:00");
        assertThat(open.isRegularMonitoringWindow(open.now())).isTrue();
        assertThat(close.isRegularMonitoringWindow(close.now())).isTrue();
    }

    @Test
    void utcClockIsConvertedToKoreaDateAndTime() {
        Clock utc = Clock.fixed(
                java.time.Instant.parse("2026-08-23T23:30:00Z"),
                java.time.ZoneOffset.UTC);
        var policy = new KrxRegularMarketSessionPolicy(properties(), utc);

        assertThat(policy.now().toLocalDateTime())
                .isEqualTo(LocalDateTime.parse("2026-08-24T08:30:00"));
    }

    @Test
    void configurationRequiresOrderedTimesAndPositiveRetryInterval() {
        var properties = properties();
        properties.setMorningDeadline(LocalTime.of(9, 0));
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class);

        properties = properties();
        properties.setRetryInterval(Duration.ZERO);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryBecomesDueAtConfiguredFiveMinuteBoundary() {
        var policy = policyAt("2026-08-24T08:35:00");
        ZonedDateTime previous = ZonedDateTime.of(
                LocalDateTime.parse("2026-08-24T08:30:00"),
                KrxRegularMarketSessionPolicy.KST);

        assertThat(policy.isRetryDue(policy.now().minusSeconds(1), previous))
                .isFalse();
        assertThat(policy.isRetryDue(policy.now(), previous)).isTrue();
    }

    private KrxRegularMarketSessionPolicy policyAt(String koreaDateTime) {
        var dateTime = LocalDateTime.parse(koreaDateTime);
        Clock clock = Clock.fixed(
                dateTime.atZone(KrxRegularMarketSessionPolicy.KST).toInstant(),
                java.time.ZoneOffset.UTC);
        return new KrxRegularMarketSessionPolicy(properties(), clock);
    }

    private OperationalRealtimeAutomationProperties properties() {
        var properties = new OperationalRealtimeAutomationProperties();
        properties.validate();
        return properties;
    }
}
