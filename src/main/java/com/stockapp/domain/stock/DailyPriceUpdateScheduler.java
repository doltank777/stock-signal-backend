package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceUpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@Profile("!daily-price-load & !daily-price-update & !screening-run")
@ConditionalOnProperty(
        prefix = "kis.daily-price.update.scheduler",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class DailyPriceUpdateScheduler {

    private final DailyPriceUpdateService dailyPriceUpdateService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            cron = "${kis.daily-price.update.scheduler.cron:0 20 16 * * MON-FRI}",
            zone = "Asia/Seoul"
    )
    public void updateDailyPrices() {
        if (!running.compareAndSet(false, true)) {
            log.warn("일봉 업데이트 Scheduler 실행 중이므로 중복 실행을 건너뜁니다.");
            return;
        }

        log.info("일봉 업데이트 Scheduler 시작");
        try {
            DailyPriceUpdateResult result = dailyPriceUpdateService.update();
            log.info("일봉 업데이트 Scheduler 완료 - updated: {}, upToDate: {}, "
                            + "noNewData: {}, noBaseHistory: {}, failed: {}, apiCalls: {}, savedRows: {}",
                    result.getUpdatedStockCount(), result.getUpToDateStockCount(),
                    result.getNoNewDataStockCount(), result.getNoBaseHistoryStockCount(),
                    result.getFailedStockCount(), result.getApiCallCount(),
                    result.getSavedDailyPriceCount());
        } catch (RuntimeException e) {
            log.error("일봉 업데이트 Scheduler 실패", e);
        } finally {
            running.set(false);
        }
    }
}
