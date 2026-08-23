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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyPriceFinalizationRecoveryService recoveryService;
    private final KrxTradingCalendar tradingCalendar;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(KST));
            LocalDate previousTradingDay =
                    tradingCalendar.previousTradingDay(today);
            var latest = recoveryService.findLatestExecution();
            LocalDate recoveredTarget = null;
            if (latest.isPresent() && !latest.get().ready()) {
                recoveredTarget = latest.get().targetTradeDate();
                recover(recoveredTarget, today, "latest incomplete execution");
            }

            if (previousTradingDay.equals(recoveredTarget)) {
                log.info("Daily Price Finalization previous trading day check skipped - "
                                + "today: {}, previousTradingDay: {}, reason: already recovered",
                        today, previousTradingDay);
                return;
            }

            var previousExecution =
                    recoveryService.findExecution(previousTradingDay);
            if (previousExecution.isPresent()
                    && previousExecution.get().ready()) {
                log.info("Daily Price Finalization previous trading day recovery skipped - "
                                + "today: {}, previousTradingDay: {}, reason: already ready",
                        today, previousTradingDay);
                return;
            }

            String reason = previousExecution.isEmpty()
                    ? "missing execution" : "incomplete execution";
            recover(previousTradingDay, today, reason);
        } catch (DailyPriceFinalizationAlreadyRunningException exception) {
            log.warn("Finalization already running; startup recovery skipped");
        } catch (RuntimeException exception) {
            log.error("Daily Price Finalization startup recovery failed; "
                    + "application startup will continue", exception);
        }
    }

    private void recover(
            LocalDate targetTradeDate,
            LocalDate today,
            String reason
    ) {
        log.warn("Daily Price Finalization startup recovery started - "
                        + "today: {}, targetTradeDate: {}, reason: {}",
                today, targetTradeDate, reason);
        var result = recoveryService.recover(targetTradeDate);
        log.info("Daily Price Finalization startup recovery completed - "
                        + "today: {}, targetTradeDate: {}, status: {}, ready: {}",
                today, result.execution().targetTradeDate(),
                result.execution().status(), result.execution().ready());
    }
}
