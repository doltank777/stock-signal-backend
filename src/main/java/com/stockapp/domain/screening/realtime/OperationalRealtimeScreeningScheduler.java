package com.stockapp.domain.screening.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !daily-price-load & !daily-price-update & !screening-run "
        + "& !schema-validate & !kis-websocket-probe & !kis-daily-price-probe")
@ConditionalOnProperty(
        prefix = "operational-screening.realtime.morning",
        name = "enabled",
        havingValue = "true")
@RequiredArgsConstructor
public class OperationalRealtimeScreeningScheduler {

    private final OperationalMorningRunCoordinator coordinator;

    @Scheduled(
            cron = "${operational-screening.realtime.morning.cron:0 30,35,40,45,50,55 8 * * MON-FRI}",
            zone = "Asia/Seoul")
    public void runMorningTick() {
        coordinator.executeTick();
    }
}
