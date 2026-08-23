package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionSnapshot;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationRecoveryResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyPriceFinalizationStartupRecoveryRunnerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 21);
    private static final LocalDate OLDER = LocalDate.of(2026, 8, 20);
    private static final Clock MONDAY_CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void recoversMissingPreviousTradingDay() {
        Fixture fixture = fixture(MONDAY_CLOCK, FRIDAY);
        when(fixture.service.findLatestExecution()).thenReturn(Optional.empty());
        when(fixture.service.findExecution(FRIDAY)).thenReturn(Optional.empty());
        when(fixture.service.recover(FRIDAY)).thenReturn(recovery(FRIDAY));

        fixture.runner.run(arguments());

        verify(fixture.calendar).previousTradingDay(MONDAY);
        verify(fixture.service).recover(FRIDAY);
    }

    @Test
    void skipsReadyPreviousTradingDayIdempotently() {
        Fixture fixture = fixture(MONDAY_CLOCK, FRIDAY);
        when(fixture.service.findLatestExecution()).thenReturn(
                Optional.of(snapshot(FRIDAY, true)));
        when(fixture.service.findExecution(FRIDAY)).thenReturn(
                Optional.of(snapshot(FRIDAY, true)));

        fixture.runner.run(arguments());
        fixture.runner.run(arguments());

        verify(fixture.service, never()).recover(FRIDAY);
    }

    @Test
    void recoversIncompletePreviousTradingDay() {
        Fixture fixture = fixture(MONDAY_CLOCK, FRIDAY);
        when(fixture.service.findLatestExecution()).thenReturn(
                Optional.of(snapshot(FRIDAY, false)));
        when(fixture.service.recover(FRIDAY)).thenReturn(recovery(FRIDAY));

        fixture.runner.run(arguments());

        verify(fixture.service).recover(FRIDAY);
        verify(fixture.service, never()).findExecution(FRIDAY);
    }

    @Test
    void recoversLatestIncompleteThenDistinctPreviousDayAtMostOnceEach() {
        Fixture fixture = fixture(MONDAY_CLOCK, FRIDAY);
        when(fixture.service.findLatestExecution()).thenReturn(
                Optional.of(snapshot(OLDER, false)));
        when(fixture.service.recover(OLDER)).thenReturn(recovery(OLDER));
        when(fixture.service.findExecution(FRIDAY)).thenReturn(Optional.empty());
        when(fixture.service.recover(FRIDAY)).thenReturn(recovery(FRIDAY));

        fixture.runner.run(arguments());

        verify(fixture.service).recover(OLDER);
        verify(fixture.service).recover(FRIDAY);
    }

    @Test
    void calendarUnavailableFailsClosedBeforeExecutionLookup() {
        var service = mock(DailyPriceFinalizationRecoveryService.class);
        var calendar = mock(KrxTradingCalendar.class);
        when(calendar.previousTradingDay(MONDAY)).thenThrow(
                new TradingCalendarUnavailableException(
                        MONDAY, "calendar coverage contains missing dates"));

        runner(service, calendar, MONDAY_CLOCK).run(arguments());

        verify(calendar).previousTradingDay(MONDAY);
        verifyNoInteractions(service);
    }

    @Test
    void weekendStartupUsesCalendarReturnedFridayWithoutTodayGuard() {
        Clock sundayClock = Clock.fixed(
                Instant.parse("2026-08-23T03:00:00Z"), ZoneOffset.UTC);
        Fixture fixture = fixture(sundayClock, FRIDAY);
        when(fixture.service.findLatestExecution()).thenReturn(Optional.empty());
        when(fixture.service.findExecution(FRIDAY)).thenReturn(Optional.empty());
        when(fixture.service.recover(FRIDAY)).thenReturn(recovery(FRIDAY));

        fixture.runner.run(arguments());

        verify(fixture.calendar).previousTradingDay(
                LocalDate.of(2026, 8, 23));
        verify(fixture.calendar, never()).isTradingDay(
                org.mockito.ArgumentMatchers.any());
        verify(fixture.service).recover(FRIDAY);
    }

    @Test
    void postHolidayStartupUsesCalendarReturnedPreviousTradingDay() {
        LocalDate today = LocalDate.of(2026, 10, 6);
        LocalDate beforeHoliday = LocalDate.of(2026, 10, 2);
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-05T15:30:00Z"), ZoneOffset.UTC);
        Fixture fixture = fixture(clock, beforeHoliday);
        when(fixture.service.findLatestExecution()).thenReturn(Optional.empty());
        when(fixture.service.findExecution(beforeHoliday))
                .thenReturn(Optional.empty());
        when(fixture.service.recover(beforeHoliday))
                .thenReturn(recovery(beforeHoliday));

        fixture.runner.run(arguments());

        verify(fixture.calendar).previousTradingDay(today);
        verify(fixture.service).recover(beforeHoliday);
    }

    @Test
    void recoveryFailureDoesNotFailApplicationStartupOrTryNextTarget() {
        Fixture fixture = fixture(MONDAY_CLOCK, FRIDAY);
        when(fixture.service.findLatestExecution()).thenReturn(
                Optional.of(snapshot(OLDER, false)));
        when(fixture.service.recover(OLDER)).thenThrow(
                new IllegalStateException("fatal"));

        fixture.runner.run(arguments());

        verify(fixture.service).recover(OLDER);
        verify(fixture.service, never()).findExecution(FRIDAY);
        verify(fixture.service, times(1)).recover(
                org.mockito.ArgumentMatchers.any());
    }

    private Fixture fixture(Clock clock, LocalDate previousTradingDay) {
        var service = mock(DailyPriceFinalizationRecoveryService.class);
        var calendar = mock(KrxTradingCalendar.class);
        LocalDate today = LocalDate.now(clock.withZone(
                java.time.ZoneId.of("Asia/Seoul")));
        when(calendar.previousTradingDay(today)).thenReturn(previousTradingDay);
        return new Fixture(service, calendar,
                runner(service, calendar, clock));
    }

    private DailyPriceFinalizationStartupRecoveryRunner runner(
            DailyPriceFinalizationRecoveryService service,
            KrxTradingCalendar calendar,
            Clock clock
    ) {
        return new DailyPriceFinalizationStartupRecoveryRunner(
                service, calendar, clock);
    }

    private DefaultApplicationArguments arguments() {
        return new DefaultApplicationArguments();
    }

    private DailyPriceFinalizationRecoveryResult recovery(LocalDate date) {
        return new DailyPriceFinalizationRecoveryResult(false,
                snapshot(date, true));
    }

    private DailyPriceFinalizationExecutionSnapshot snapshot(
            LocalDate date,
            boolean ready
    ) {
        return new DailyPriceFinalizationExecutionSnapshot(
                1L, date, ready
                ? DailyPriceFinalizationExecutionStatus.COMPLETED
                : DailyPriceFinalizationExecutionStatus.RUNNING,
                ready, 1, Instant.EPOCH, ready ? Instant.EPOCH : null,
                1, 1, 0, 0, 0, 0, 1, 1, 0, null);
    }

    private record Fixture(
            DailyPriceFinalizationRecoveryService service,
            KrxTradingCalendar calendar,
            DailyPriceFinalizationStartupRecoveryRunner runner
    ) {
    }
}
