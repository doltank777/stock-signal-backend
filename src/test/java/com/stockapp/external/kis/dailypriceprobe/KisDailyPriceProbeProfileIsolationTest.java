package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.domain.screening.ScreeningRunRunner;
import com.stockapp.domain.signal.realtime.RealtimeSignalPersistenceService;
import com.stockapp.domain.signal.realtime.RealtimeTradeSignalEvaluationService;
import com.stockapp.domain.stock.DailyPriceInitialLoadRunner;
import com.stockapp.domain.stock.DailyPriceUpdateRunner;
import com.stockapp.domain.stock.DailyPriceUpdateScheduler;
import com.stockapp.domain.stock.StockPriceScheduler;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.KisOAuthTokenClient;
import com.stockapp.external.kis.KisWebSocketStartupRunner;
import com.stockprobe.kisdailyprice.KisDailyPriceProbeApplication;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class KisDailyPriceProbeProfileIsolationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(context ->
                            context.getEnvironment().setActiveProfiles(
                                    "local", "kis-daily-price-probe"))
                    .withUserConfiguration(KisDailyPriceProbeApplication.class)
                    .withPropertyValues(
                            "kis.environment=virtual",
                            "kis.base-url=https://example.invalid",
                            "kis.app-key=test",
                            "kis.app-secret=test",
                            "kis-daily-price-probe.stock-code=005930");

    @Test
    void containsOnlyDailyPriceProbeBoundary() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KisDailyPriceProbeRunner.class);
            assertThat(context).hasSingleBean(KisDailyPriceProbeAnalyzer.class);
            assertThat(context).hasSingleBean(KisDailyPriceClient.class);
            assertThat(context).hasSingleBean(DirectKisAccessTokenProvider.class);
            assertThat(context).hasSingleBean(KisOAuthTokenClient.class);

            assertThat(context).doesNotHaveBean(DataSource.class);
            assertThat(context).doesNotHaveBean(EntityManagerFactory.class);
            assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
            assertThat(context).doesNotHaveBean(KisWebSocketStartupRunner.class);
            assertThat(context).doesNotHaveBean(ScreeningRunRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceInitialLoadRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceUpdateRunner.class);
            assertThat(context).doesNotHaveBean(StockPriceScheduler.class);
            assertThat(context).doesNotHaveBean(DailyPriceUpdateScheduler.class);
            assertThat(context).doesNotHaveBean(
                    RealtimeTradeSignalEvaluationService.class);
            assertThat(context).doesNotHaveBean(
                    RealtimeSignalPersistenceService.class);
        });
    }
}
