package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationRecoveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@Profile("!test & !daily-price-load & !daily-price-update & !screening-run "
        + "& !schema-validate & !kis-websocket-probe & !kis-daily-price-probe "
        + "& !daily-history-bootstrap")
@ConditionalOnProperty(
        prefix = "kis.daily-price.finalization.scheduler",
        name = "enabled",
        havingValue = "true")
@RequiredArgsConstructor
public class DailyPriceFinalizationScheduler {

    private final DailyPriceFinalizationRecoveryService recoveryService;
    private final DailyPriceFinalizationTargetDateResolver targetDateResolver;
    private final KrxTradingCalendar tradingCalendar;

    @Scheduled(
            cron = "${kis.daily-price.finalization.scheduler.cron:-}",
            zone = "${kis.daily-price.finalization.scheduler.zone:Asia/Seoul}")
    public void finalizeDailyPrices() {
        LocalDate targetTradeDate =
                targetDateResolver.resolveScheduledTargetDate();
        try {
            if (!tradingCalendar.isTradingDay(targetTradeDate)) {
                log.info("Daily Price Finalization scheduled trigger skipped - "
                                + "targetTradeDate: {}, reason: not a KRX trading day",
                        targetTradeDate);
                return;
            }
            DailyPriceFinalizationRecoveryResult result =
                    recoveryService.recover(targetTradeDate);
            log.info("Daily Price Finalization scheduled trigger completed - "
                            + "targetTradeDate: {}, alreadyReady: {}, status: {}, ready: {}",
                    targetTradeDate, result.alreadyReady(),
                    result.execution().status(), result.execution().ready());
        } catch (DailyPriceFinalizationAlreadyRunningException exception) {
            log.warn("Finalization already running; scheduled trigger skipped");
        } catch (RuntimeException exception) {
            log.error("Daily Price Finalization scheduled trigger failed - "
                    + "targetTradeDate: {}", targetTradeDate, exception);
        }
    }
}
