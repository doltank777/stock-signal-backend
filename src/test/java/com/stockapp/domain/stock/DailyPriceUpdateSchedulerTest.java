package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceUpdateResult;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyPriceUpdateSchedulerTest {

    @Test
    void invokesUpdateServiceAndReleasesGuardAfterCompletion() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        when(service.update()).thenReturn(result());
        DailyPriceUpdateScheduler scheduler = new DailyPriceUpdateScheduler(service);

        scheduler.updateDailyPrices();
        scheduler.updateDailyPrices();

        verify(service, times(2)).update();
    }

    @Test
    void skipsOverlappingExecution() throws Exception {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.update()).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return result();
        });
        DailyPriceUpdateScheduler scheduler = new DailyPriceUpdateScheduler(service);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> firstExecution = executor.submit(scheduler::updateDailyPrices);
            started.await();
            scheduler.updateDailyPrices();
            release.countDown();
            firstExecution.get();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        verify(service).update();
    }

    @Test
    void releasesGuardAfterFailureForNextSchedule() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        when(service.update())
                .thenThrow(new IllegalStateException("failed"))
                .thenReturn(result());
        DailyPriceUpdateScheduler scheduler = new DailyPriceUpdateScheduler(service);

        scheduler.updateDailyPrices();
        scheduler.updateDailyPrices();

        verify(service, times(2)).update();
    }

    @Test
    void preservesInterruptFlagWhenServiceAborts() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        when(service.update()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        });
        DailyPriceUpdateScheduler scheduler = new DailyPriceUpdateScheduler(service);

        try {
            scheduler.updateDailyPrices();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void usesConfiguredCronAndKoreaTimezone() throws Exception {
        Method method = DailyPriceUpdateScheduler.class
                .getDeclaredMethod("updateDailyPrices");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo(
                "${kis.daily-price.update.scheduler.cron:0 20 16 * * MON-FRI}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    private DailyPriceUpdateResult result() {
        return DailyPriceUpdateResult.builder().build();
    }
}
