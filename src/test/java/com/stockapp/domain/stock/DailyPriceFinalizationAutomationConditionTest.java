package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DailyPriceFinalizationAutomationConditionTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(DailyPriceFinalizationRecoveryService.class,
                            () -> mock(DailyPriceFinalizationRecoveryService.class))
                    .withBean(DailyPriceFinalizationTargetDateResolver.class,
                            () -> mock(DailyPriceFinalizationTargetDateResolver.class))
                    .withUserConfiguration(
                            DailyPriceFinalizationScheduler.class,
                            DailyPriceFinalizationStartupRecoveryRunner.class);

    @Test
    void bothAutomationComponentsAreDisabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(DailyPriceFinalizationScheduler.class)
                .doesNotHaveBean(
                        DailyPriceFinalizationStartupRecoveryRunner.class));
    }

    @Test
    void eachComponentRequiresItsOwnEnabledProperty() {
        contextRunner.withPropertyValues(
                        "kis.daily-price.finalization.scheduler.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(DailyPriceFinalizationScheduler.class)
                        .doesNotHaveBean(
                                DailyPriceFinalizationStartupRecoveryRunner.class));
        contextRunner.withPropertyValues(
                        "kis.daily-price.finalization.startup-recovery.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(DailyPriceFinalizationScheduler.class)
                        .hasSingleBean(
                                DailyPriceFinalizationStartupRecoveryRunner.class));
    }

    @Test
    void automationIsIsolatedFromBatchProbeAndTestProfiles() {
        for (String profile : new String[]{"test", "daily-price-load",
                "daily-price-update", "screening-run", "schema-validate",
                "kis-websocket-probe", "kis-daily-price-probe"}) {
            contextRunner.withInitializer(context -> context.getEnvironment()
                            .setActiveProfiles(profile))
                    .withPropertyValues(
                            "kis.daily-price.finalization.scheduler.enabled=true",
                            "kis.daily-price.finalization.startup-recovery.enabled=true")
                    .run(context -> assertThat(context)
                            .doesNotHaveBean(DailyPriceFinalizationScheduler.class)
                            .doesNotHaveBean(
                                    DailyPriceFinalizationStartupRecoveryRunner.class));
        }
    }
}
