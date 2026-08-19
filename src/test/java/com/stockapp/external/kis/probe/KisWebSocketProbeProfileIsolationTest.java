package com.stockapp.external.kis.probe;

import com.stockapp.domain.screening.ScreeningRunRunner;
import com.stockapp.domain.signal.realtime.RealtimeSignalPersistenceService;
import com.stockapp.domain.signal.realtime.RealtimeTradeSignalEvaluationService;
import com.stockapp.domain.stock.DailyPriceInitialLoadRunner;
import com.stockapp.domain.stock.DailyPriceUpdateRunner;
import com.stockapp.domain.stock.DailyPriceUpdateScheduler;
import com.stockapp.domain.stock.StockPriceScheduler;
import com.stockapp.external.kis.KisWebSocketStartupRunner;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class KisWebSocketProbeProfileIsolationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withInitializer(context ->
                            context.getEnvironment().setActiveProfiles(
                                    "local", "kis-websocket-probe"))
                    .withUserConfiguration(KisWebSocketProbeApplication.class)
                    .withPropertyValues(
                            "kis.environment=virtual",
                            "kis.base-url=https://example.invalid",
                            "kis.web-socket-url=ws://example.invalid",
                            "kis.app-key=test",
                            "kis.app-secret=test",
                            "kis-websocket-probe.stock-codes=005930");

    @Test
    void containsOnlyProbeBoundaryWithoutDatabaseRedisOrBusinessRunners() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(KisWebSocketProbeRunner.class);
            assertThat(context).hasSingleBean(KisWebSocketProbeHandler.class);
            assertThat(context).hasSingleBean(NoCacheKisWebSocketApprovalKeyCache.class);

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
