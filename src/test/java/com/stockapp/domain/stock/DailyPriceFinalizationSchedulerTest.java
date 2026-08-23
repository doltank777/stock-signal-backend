package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionSnapshot;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationRecoveryResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyPriceFinalizationSchedulerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    @Test
    void delegatesScheduledDateToRecoveryService() {
        var recoveryService = mock(DailyPriceFinalizationRecoveryService.class);
        var resolver = mock(DailyPriceFinalizationTargetDateResolver.class);
        var calendar = mock(KrxTradingCalendar.class);
        when(resolver.resolveScheduledTargetDate()).thenReturn(DATE);
        when(calendar.isTradingDay(DATE)).thenReturn(true);
        when(recoveryService.recover(DATE)).thenReturn(
                new DailyPriceFinalizationRecoveryResult(false,
                        snapshot(false)));

        new DailyPriceFinalizationScheduler(
                recoveryService, resolver, calendar).finalizeDailyPrices();

        verify(calendar).isTradingDay(DATE);
        verify(calendar, never()).previousTradingDay(
                org.mockito.ArgumentMatchers.any());
        verify(recoveryService).recover(DATE);
    }

    @Test
    void skipsClosedDateBeforeRecoveryStarts() {
        var recoveryService = mock(DailyPriceFinalizationRecoveryService.class);
        var resolver = mock(DailyPriceFinalizationTargetDateResolver.class);
        var calendar = mock(KrxTradingCalendar.class);
        when(resolver.resolveScheduledTargetDate()).thenReturn(DATE);
        when(calendar.isTradingDay(DATE)).thenReturn(false);

        new DailyPriceFinalizationScheduler(
                recoveryService, resolver, calendar).finalizeDailyPrices();

        verify(calendar).isTradingDay(DATE);
        verify(calendar, never()).previousTradingDay(
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(recoveryService);
    }

    @Test
    void calendarUnavailableFailsClosedBeforeRecoveryStarts() {
        var recoveryService = mock(DailyPriceFinalizationRecoveryService.class);
        var resolver = mock(DailyPriceFinalizationTargetDateResolver.class);
        var calendar = mock(KrxTradingCalendar.class);
        when(resolver.resolveScheduledTargetDate()).thenReturn(DATE);
        when(calendar.isTradingDay(DATE)).thenThrow(
                new TradingCalendarUnavailableException(
                        DATE, "date is outside synchronized coverage"));

        new DailyPriceFinalizationScheduler(
                recoveryService, resolver, calendar).finalizeDailyPrices();

        verify(calendar).isTradingDay(DATE);
        verify(calendar, never()).previousTradingDay(
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(recoveryService);
    }

    @Test
    void alreadyRunningIsTreatedAsSkippedTrigger() {
        var recoveryService = mock(DailyPriceFinalizationRecoveryService.class);
        var resolver = mock(DailyPriceFinalizationTargetDateResolver.class);
        var calendar = mock(KrxTradingCalendar.class);
        when(resolver.resolveScheduledTargetDate()).thenReturn(DATE);
        when(calendar.isTradingDay(DATE)).thenReturn(true);
        when(recoveryService.recover(DATE)).thenThrow(
                new DailyPriceFinalizationAlreadyRunningException());

        new DailyPriceFinalizationScheduler(
                recoveryService, resolver, calendar).finalizeDailyPrices();

        verify(recoveryService).recover(DATE);
    }

    private DailyPriceFinalizationExecutionSnapshot snapshot(boolean ready) {
        return new DailyPriceFinalizationExecutionSnapshot(
                1L, DATE, DailyPriceFinalizationExecutionStatus.COMPLETED,
                ready, 1, Instant.EPOCH, Instant.EPOCH,
                1, 1, 0, 0, 0, 0, 1, 1, 0, null);
    }
}
