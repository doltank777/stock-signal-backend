package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceUpdateResult;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyPriceUpdateSchedulerTest {

    private static final LocalDate KST_TODAY = LocalDate.of(2026, 8, 24);
    private static final Clock KST_BOUNDARY_CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void invokesUpdateServiceOnTradingDayUsingKoreaDate() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(KST_TODAY)).thenReturn(true);
        when(service.update()).thenReturn(result());
        DailyPriceUpdateScheduler scheduler = scheduler(service, calendar);

        scheduler.updateDailyPrices();

        verify(calendar).isTradingDay(KST_TODAY);
        verify(service).update();
    }

    @Test
    void skipsClosedDayWithoutFallbackAndReleasesGuard() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(KST_TODAY)).thenReturn(false, true);
        when(service.update()).thenReturn(result());
        DailyPriceUpdateScheduler scheduler = scheduler(service, calendar);

        scheduler.updateDailyPrices();
        verifyNoInteractions(service);

        scheduler.updateDailyPrices();

        verify(calendar, times(2)).isTradingDay(KST_TODAY);
        verify(calendar, never()).previousTradingDay(KST_TODAY);
        verify(service).update();
    }

    @Test
    void failsClosedWhenCalendarIsUnavailableAndReleasesGuard() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(KST_TODAY))
                .thenThrow(new TradingCalendarUnavailableException(
                        KST_TODAY, "coverage gap"))
                .thenReturn(true);
        when(service.update()).thenReturn(result());
        DailyPriceUpdateScheduler scheduler = scheduler(service, calendar);

        scheduler.updateDailyPrices();
        verifyNoInteractions(service);

        scheduler.updateDailyPrices();

        verify(calendar, times(2)).isTradingDay(KST_TODAY);
        verify(calendar, never()).previousTradingDay(KST_TODAY);
        verify(service).update();
    }

    @Test
    void skipsOverlappingExecution() throws Exception {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(KST_TODAY)).thenReturn(true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.update()).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return result();
        });
        DailyPriceUpdateScheduler scheduler = scheduler(service, calendar);
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

        verify(calendar).isTradingDay(KST_TODAY);
        verify(service).update();
    }

    @Test
    void releasesGuardAfterFailureForNextSchedule() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(KST_TODAY)).thenReturn(true);
        when(service.update())
                .thenThrow(new IllegalStateException("failed"))
                .thenReturn(result());
        DailyPriceUpdateScheduler scheduler = scheduler(service, calendar);

        scheduler.updateDailyPrices();
        scheduler.updateDailyPrices();

        verify(service, times(2)).update();
    }

    @Test
    void preservesInterruptFlagWhenServiceAborts() {
        DailyPriceUpdateService service = mock(DailyPriceUpdateService.class);
        KrxTradingCalendar calendar = mock(KrxTradingCalendar.class);
        when(calendar.isTradingDay(KST_TODAY)).thenReturn(true);
        when(service.update()).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        });
        DailyPriceUpdateScheduler scheduler = scheduler(service, calendar);

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

    private DailyPriceUpdateScheduler scheduler(
            DailyPriceUpdateService service, KrxTradingCalendar calendar) {
        return new DailyPriceUpdateScheduler(
                service, calendar, KST_BOUNDARY_CLOCK);
    }

    private DailyPriceUpdateResult result() {
        return DailyPriceUpdateResult.builder().build();
    }
}
