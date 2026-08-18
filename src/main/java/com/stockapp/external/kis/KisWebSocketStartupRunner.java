package com.stockapp.external.kis;

import com.stockapp.domain.screening.realtime.RealtimeScreeningSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local & !test & !daily-price-load & !daily-price-update & !screening-run")
@RequiredArgsConstructor
public class KisWebSocketStartupRunner implements ApplicationRunner {

    private final RealtimeScreeningSubscriptionService subscriptionService;

    @Override
    public void run(ApplicationArguments args) {
        subscriptionService.initialize();
    }
}
