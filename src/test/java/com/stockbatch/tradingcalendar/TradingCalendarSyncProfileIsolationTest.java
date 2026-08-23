package com.stockbatch.tradingcalendar;

import com.stockapp.domain.screening.ScreeningRunRunner;
import com.stockapp.domain.stock.DailyPriceFinalizationScheduler;
import com.stockapp.domain.stock.DailyPriceFinalizationStartupRecoveryRunner;
import com.stockapp.domain.stock.DailyPriceInitialLoadRunner;
import com.stockapp.domain.stock.DailyPriceUpdateRunner;
import com.stockapp.domain.stock.DailyPriceUpdateScheduler;
import com.stockapp.domain.stock.StockPriceScheduler;
import com.stockapp.external.kis.KisWebSocketStartupRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TradingCalendarSyncProfileIsolationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            TradingCalendarSyncApplication.class)
                    .withPropertyValues(
                            "spring.profiles.active=trading-calendar-sync",
                            "spring.jpa.hibernate.ddl-auto=create-drop");

    @Test
    void createsOnlyTheDedicatedRunnerWithoutBusinessStartupComponents() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TradingCalendarSyncRunner.class);
            assertThat(context).doesNotHaveBean(StockPriceScheduler.class);
            assertThat(context).doesNotHaveBean(DailyPriceUpdateScheduler.class);
            assertThat(context).doesNotHaveBean(
                    DailyPriceFinalizationScheduler.class);
            assertThat(context).doesNotHaveBean(KisWebSocketStartupRunner.class);
            assertThat(context).doesNotHaveBean(ScreeningRunRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceInitialLoadRunner.class);
            assertThat(context).doesNotHaveBean(DailyPriceUpdateRunner.class);
            assertThat(context).doesNotHaveBean(
                    DailyPriceFinalizationStartupRecoveryRunner.class);
        });
    }
}
