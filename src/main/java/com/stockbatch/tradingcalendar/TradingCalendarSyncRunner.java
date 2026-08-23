package com.stockbatch.tradingcalendar;

import com.stockapp.domain.stock.KrxTradingCalendarSynchronizer;
import com.stockapp.domain.stock.dto.KrxTradingCalendarSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
public class TradingCalendarSyncRunner implements ApplicationRunner {

    private final TradingCalendarSyncProperties properties;
    private final KrxTradingCalendarSynchronizer synchronizer;

    @Override
    public void run(ApplicationArguments args) {
        execute();
    }

    KrxTradingCalendarSyncResult execute() {
        LocalDate startDate = properties.resolvedStartDate();
        LocalDate endDate = properties.resolvedEndDate(startDate);
        try {
            KrxTradingCalendarSyncResult result =
                    synchronizer.synchronize(startDate, endDate);
            log.info("[KRX TRADING CALENDAR SYNC]\n\nstartDate={}"
                            + "\nendDate={}\nreceivedRows={}\ninserted={}"
                            + "\nupdated={}\nunchanged={}\nstartedAt={}"
                            + "\nfinishedAt={}",
                    startDate, endDate, result.receivedCount(),
                    result.insertedCount(), result.updatedCount(),
                    result.unchangedCount(), result.startedAt(),
                    result.finishedAt());
            return result;
        } catch (RuntimeException exception) {
            log.error("KRX Trading Calendar sync failed startDate={} endDate={} errorType={} message={}",
                    startDate, endDate, exception.getClass().getSimpleName(),
                    exception.getMessage());
            throw exception;
        }
    }
}
