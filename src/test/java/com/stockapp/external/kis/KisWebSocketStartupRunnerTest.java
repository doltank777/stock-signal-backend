package com.stockapp.external.kis;

import com.stockapp.domain.screening.realtime.RealtimeScreeningSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KisWebSocketStartupRunnerTest {

    @Test
    void delegatesInitializationExactlyOnce() {
        RealtimeScreeningSubscriptionService subscriptionService =
                mock(RealtimeScreeningSubscriptionService.class);
        KisWebSocketStartupRunner runner = new KisWebSocketStartupRunner(
                subscriptionService);

        runner.run(mock(ApplicationArguments.class));

        verify(subscriptionService).initialize();
    }
}
