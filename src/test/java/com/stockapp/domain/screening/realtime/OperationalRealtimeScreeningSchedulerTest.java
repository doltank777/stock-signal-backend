package com.stockapp.domain.screening.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OperationalRealtimeScreeningSchedulerTest {

    @Test
    void delegatesTickAndKeepsExplicitDisabledByDefaultCondition()
            throws Exception {
        var coordinator = mock(OperationalMorningRunCoordinator.class);
        new OperationalRealtimeScreeningScheduler(coordinator)
                .runMorningTick();
        verify(coordinator).executeTick();

        Scheduled scheduled = OperationalRealtimeScreeningScheduler.class
                .getDeclaredMethod("runMorningTick")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).contains("30,35,40,45,50,55");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        ConditionalOnProperty condition =
                OperationalRealtimeScreeningScheduler.class
                        .getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }
}
