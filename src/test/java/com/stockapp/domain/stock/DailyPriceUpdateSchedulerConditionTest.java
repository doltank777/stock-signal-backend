package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DailyPriceUpdateSchedulerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(DailyPriceUpdateService.class,
                    () -> mock(DailyPriceUpdateService.class))
            .withBean(KrxTradingCalendar.class,
                    () -> mock(KrxTradingCalendar.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withUserConfiguration(DailyPriceUpdateScheduler.class);

    @Test
    void schedulerIsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(DailyPriceUpdateScheduler.class));
    }

    @Test
    void schedulerIsEnabledExplicitlyInLocalProfile() {
        contextRunner
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues(
                        "kis.daily-price.update.scheduler.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(DailyPriceUpdateScheduler.class));
    }

    @Test
    void schedulerIsDisabledDuringInitialLoadBatch() {
        contextRunner
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("daily-price-load"))
                .withPropertyValues(
                        "kis.daily-price.update.scheduler.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(DailyPriceUpdateScheduler.class));
    }

    @Test
    void schedulerIsDisabledDuringManualUpdateBatch() {
        contextRunner
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("daily-price-update"))
                .withPropertyValues(
                        "kis.daily-price.update.scheduler.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(DailyPriceUpdateScheduler.class));
    }

    @Test
    void schedulerIsDisabledDuringScreeningRun() {
        contextRunner
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles("screening-run"))
                .withPropertyValues(
                        "kis.daily-price.update.scheduler.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(DailyPriceUpdateScheduler.class));
    }
}
