package com.stockapp.external.kis.tradingcalendarprobe;

import com.stockapp.domain.screening.ScreeningRunRunner;
import com.stockapp.domain.stock.DailyPriceFinalizationScheduler;
import com.stockapp.domain.stock.DailyPriceFinalizationStartupRecoveryRunner;
import com.stockapp.domain.stock.DailyPriceInitialLoadRunner;
import com.stockapp.domain.stock.DailyPriceUpdateRunner;
import com.stockapp.domain.stock.DailyPriceUpdateScheduler;
import com.stockapp.domain.stock.KrxTradingCalendarSynchronizer;
import com.stockapp.domain.stock.KrxTradingCalendarWriter;
import com.stockapp.domain.stock.StockPriceScheduler;
import com.stockapp.external.kis.KisTradingCalendarClient;
import com.stockapp.external.kis.KisTradingCalendarSleeper;
import com.stockapp.external.kis.KisWebSocketStartupRunner;
import com.stockapp.external.kis.KisOAuthTokenClient;
import com.stockprobe.kistradingcalendar.KisTradingCalendarProbeApplication;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class KisTradingCalendarProbeProfileIsolationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(context ->
                            context.getEnvironment().setActiveProfiles(
                                    "local", "kis-trading-calendar-probe"))
                    .withUserConfiguration(
                            KisTradingCalendarProbeApplication.class)
                    .withPropertyValues(
                            "kis.environment=virtual",
                            "kis.base-url=https://example.invalid",
                            "kis.app-key=test",
                            "kis.app-secret=test",
                            "kis.trading-calendar.base-url=https://calendar.example.invalid",
                            "kis.trading-calendar.app-key=calendar-test",
                            "kis.trading-calendar.app-secret=calendar-secret-test");

    @Test
    void containsOnlyReadOnlyTradingCalendarProbeBoundary() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(
                    KisTradingCalendarProbeRunner.class);
            assertThat(context).hasSingleBean(KisTradingCalendarClient.class);
            assertThat(context).hasSingleBean(
                    DirectKisTradingCalendarAccessTokenProvider.class);
            assertThat(context).hasSingleBean(KisOAuthTokenClient.class);
            assertThat(context).hasSingleBean(KisTradingCalendarSleeper.class);

            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context).doesNotHaveBean(EntityManagerFactory.class);
            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
            assertThat(context).doesNotHaveBean(KrxTradingCalendarWriter.class);
            assertThat(context).doesNotHaveBean(
                    KrxTradingCalendarSynchronizer.class);
            assertThat(context).doesNotHaveBean(KisWebSocketStartupRunner.class);
            assertThat(context).doesNotHaveBean(ScreeningRunRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceInitialLoadRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceUpdateRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceUpdateScheduler.class);
            assertThat(context).doesNotHaveBean(StockPriceScheduler.class);
            assertThat(context).doesNotHaveBean(
                    DailyPriceFinalizationScheduler.class);
            assertThat(context).doesNotHaveBean(
                    DailyPriceFinalizationStartupRecoveryRunner.class);
        });
    }

    @Test
    void contextCanStartWithoutCalendarCredentialsUntilExplicitProbeCall() {
        new ApplicationContextRunner()
                .withInitializer(context ->
                        context.getEnvironment().setActiveProfiles(
                                "local", "kis-trading-calendar-probe"))
                .withUserConfiguration(
                        KisTradingCalendarProbeApplication.class)
                .withPropertyValues(
                        "kis.environment=virtual",
                        "kis.base-url=https://example.invalid",
                        "kis.app-key=test",
                        "kis.app-secret=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            KisTradingCalendarProbeRunner.class);
                });
    }
}
