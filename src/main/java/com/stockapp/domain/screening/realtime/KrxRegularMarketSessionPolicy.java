package com.stockapp.domain.screening.realtime;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class KrxRegularMarketSessionPolicy {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OperationalRealtimeAutomationProperties properties;
    private final Clock clock;

    public KrxRegularMarketSessionPolicy(
            OperationalRealtimeAutomationProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.clock = clock;
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(clock.withZone(KST));
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    public boolean isStartupRecoveryWindow() {
        LocalTime time = now().toLocalTime();
        return !time.isBefore(properties.getMorningStart())
                && !time.isAfter(properties.getMarketClose());
    }

    public boolean isMorningPreparationWindow(ZonedDateTime now) {
        LocalTime time = now.withZoneSameInstant(KST).toLocalTime();
        return !time.isBefore(properties.getMorningStart())
                && !time.isAfter(properties.getMorningDeadline());
    }

    public boolean isMorningDeadlineReached(ZonedDateTime now) {
        return !now.withZoneSameInstant(KST).toLocalTime()
                .isBefore(properties.getMorningDeadline());
    }

    public boolean isRetryDue(
            ZonedDateTime now,
            ZonedDateTime previousAttempt
    ) {
        if (previousAttempt == null) {
            return true;
        }
        return !now.toInstant().isBefore(previousAttempt.toInstant().plus(
                properties.getRetryInterval()));
    }

    public boolean isRegularMonitoringWindow(ZonedDateTime now) {
        LocalTime time = now.withZoneSameInstant(KST).toLocalTime();
        return !time.isBefore(properties.getMarketOpen())
                && !time.isAfter(properties.getMarketClose());
    }
}
