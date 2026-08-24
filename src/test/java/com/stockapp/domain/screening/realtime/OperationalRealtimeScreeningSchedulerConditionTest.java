package com.stockapp.domain.screening.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OperationalRealtimeScreeningSchedulerConditionTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(OperationalMorningRunCoordinator.class,
                            () -> mock(OperationalMorningRunCoordinator.class))
                    .withUserConfiguration(
                            OperationalRealtimeScreeningScheduler.class);

    @Test
    void disabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(
                OperationalRealtimeScreeningScheduler.class));
    }

    @Test
    void enabledExplicitlyInLocalProfile() {
        contextRunner
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("local"))
                .withPropertyValues(
                        "operational-screening.realtime.morning.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(
                        OperationalRealtimeScreeningScheduler.class));
    }

    @Test
    void disabledInTestAndProbeProfiles() {
        contextRunner
                .withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("test", "kis-websocket-probe"))
                .withPropertyValues(
                        "operational-screening.realtime.morning.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(
                        OperationalRealtimeScreeningScheduler.class));
    }
}
