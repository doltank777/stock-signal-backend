package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("!test & !daily-price-load & !daily-price-update & !screening-run "
        + "& !schema-validate & !kis-websocket-probe & !kis-daily-price-probe")
@ConditionalOnProperty(
        prefix = "kis.daily-price.finalization.startup-recovery",
        name = "enabled",
        havingValue = "true")
@RequiredArgsConstructor
public class DailyPriceFinalizationStartupRecoveryRunner
        implements ApplicationRunner {

    private final DailyPriceFinalizationRecoveryService recoveryService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var latest = recoveryService.findLatestExecution();
            if (latest.isEmpty() || latest.get().ready()) {
                log.info("Daily Price Finalization startup recovery skipped - "
                        + "no latest incomplete execution");
                return;
            }
            var result = recoveryService.recover(
                    latest.get().targetTradeDate());
            log.info("Daily Price Finalization startup recovery completed - "
                            + "targetTradeDate: {}, status: {}, ready: {}",
                    result.execution().targetTradeDate(),
                    result.execution().status(), result.execution().ready());
        } catch (DailyPriceFinalizationAlreadyRunningException exception) {
            log.warn("Finalization already running; startup recovery skipped");
        } catch (RuntimeException exception) {
            log.error("Daily Price Finalization startup recovery failed; "
                    + "application startup will continue", exception);
        }
    }
}
