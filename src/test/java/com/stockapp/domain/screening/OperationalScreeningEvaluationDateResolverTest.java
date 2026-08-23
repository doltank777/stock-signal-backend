package com.stockapp.domain.screening;

import com.stockapp.domain.stock.DailyPriceFinalizationExecutionStatus;
import com.stockapp.domain.stock.DailyPriceFinalizationRecoveryService;
import com.stockapp.domain.stock.KrxTradingCalendar;
import com.stockapp.domain.stock.TradingCalendarUnavailableException;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalScreeningEvaluationDateResolverTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 24);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 8, 21);
    private static final Clock KST_BOUNDARY_CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T15:30:00Z"), ZoneOffset.UTC);

    @Mock KrxTradingCalendar tradingCalendar;
    @Mock DailyPriceFinalizationRecoveryService recoveryService;

    @Test
    void resolvesReadyPreviousTradingDayUsingKoreaDate() {
        when(tradingCalendar.isTradingDay(MONDAY)).thenReturn(true);
        when(tradingCalendar.previousTradingDay(MONDAY)).thenReturn(FRIDAY);
        when(recoveryService.findExecution(FRIDAY)).thenReturn(Optional.of(
                snapshot(FRIDAY,
                        DailyPriceFinalizationExecutionStatus.COMPLETED, true)));

        var result = resolver().resolve();

        assertThat(result.status())
                .isEqualTo(OperationalScreeningReadinessStatus.READY);
        assertThat(result.today()).isEqualTo(MONDAY);
        assertThat(result.expectedEvaluationDate()).contains(FRIDAY);
        verify(tradingCalendar).isTradingDay(MONDAY);
        verify(tradingCalendar).previousTradingDay(MONDAY);
        verify(recoveryService).findExecution(FRIDAY);
        verify(recoveryService, never()).recover(FRIDAY);
    }

    @Test
    void usesCalendarResultAcrossAHolidayWithoutDateArithmetic() {
        LocalDate afterHoliday = LocalDate.of(2026, 9, 28);
        LocalDate beforeHoliday = LocalDate.of(2026, 9, 23);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-27T15:30:00Z"), ZoneOffset.UTC);
        when(tradingCalendar.isTradingDay(afterHoliday)).thenReturn(true);
        when(tradingCalendar.previousTradingDay(afterHoliday))
                .thenReturn(beforeHoliday);
        when(recoveryService.findExecution(beforeHoliday)).thenReturn(
                Optional.of(snapshot(beforeHoliday,
                        DailyPriceFinalizationExecutionStatus.COMPLETED, true)));

        var result = resolver(clock).resolve();

        assertThat(result.expectedEvaluationDate()).contains(beforeHoliday);
        verify(recoveryService).findExecution(beforeHoliday);
    }

    @Test
    void returnsNotTradingDayWithoutResolvingOrQueryingExecution() {
        when(tradingCalendar.isTradingDay(MONDAY)).thenReturn(false);

        var result = resolver().resolve();

        assertThat(result.status()).isEqualTo(
                OperationalScreeningReadinessStatus.NOT_TRADING_DAY);
        assertThat(result.today()).isEqualTo(MONDAY);
        assertThat(result.expectedEvaluationDate()).isEmpty();
        verify(tradingCalendar, never()).previousTradingDay(MONDAY);
        verifyNoInteractions(recoveryService);
    }

    @Test
    void propagatesUnavailableTodayCalendarWithoutFurtherQueries() {
        when(tradingCalendar.isTradingDay(MONDAY)).thenThrow(
                new TradingCalendarUnavailableException(MONDAY, "missing"));

        assertThatThrownBy(() -> resolver().resolve())
                .isInstanceOf(TradingCalendarUnavailableException.class);

        verify(tradingCalendar, never()).previousTradingDay(MONDAY);
        verifyNoInteractions(recoveryService);
    }

    @Test
    void propagatesUnavailablePreviousDayWithoutExecutionQuery() {
        when(tradingCalendar.isTradingDay(MONDAY)).thenReturn(true);
        when(tradingCalendar.previousTradingDay(MONDAY)).thenThrow(
                new TradingCalendarUnavailableException(MONDAY, "gap"));

        assertThatThrownBy(() -> resolver().resolve())
                .isInstanceOf(TradingCalendarUnavailableException.class);

        verifyNoInteractions(recoveryService);
    }

    @Test
    void returnsNotReadyWhenExactExecutionIsMissing() {
        stubTradingDay();
        when(recoveryService.findExecution(FRIDAY)).thenReturn(Optional.empty());

        var result = resolver().resolve();

        assertNotReadyForFriday(result);
        verify(recoveryService).findExecution(FRIDAY);
        verify(recoveryService, never()).findLatestExecution();
        verify(recoveryService, never()).recover(FRIDAY);
    }

    @Test
    void returnsNotReadyForEveryUnreadyExecutionState() {
        assertNotReady(DailyPriceFinalizationExecutionStatus.RUNNING, false);
        assertNotReady(DailyPriceFinalizationExecutionStatus.FAILED, false);
        assertNotReady(DailyPriceFinalizationExecutionStatus.INTERRUPTED, false);
        assertNotReady(DailyPriceFinalizationExecutionStatus.COMPLETED, false);
    }

    private void assertNotReady(
            DailyPriceFinalizationExecutionStatus status, boolean ready) {
        org.mockito.Mockito.reset(tradingCalendar, recoveryService);
        stubTradingDay();
        when(recoveryService.findExecution(FRIDAY)).thenReturn(Optional.of(
                snapshot(FRIDAY, status, ready)));

        assertNotReadyForFriday(resolver().resolve());
        verify(recoveryService, never()).recover(FRIDAY);
    }

    private void assertNotReadyForFriday(
            OperationalScreeningReadinessResult result) {
        assertThat(result.status()).isEqualTo(
                OperationalScreeningReadinessStatus.FINALIZATION_NOT_READY);
        assertThat(result.expectedEvaluationDate()).contains(FRIDAY);
    }

    private void stubTradingDay() {
        when(tradingCalendar.isTradingDay(MONDAY)).thenReturn(true);
        when(tradingCalendar.previousTradingDay(MONDAY)).thenReturn(FRIDAY);
    }

    private OperationalScreeningEvaluationDateResolver resolver() {
        return resolver(KST_BOUNDARY_CLOCK);
    }

    private OperationalScreeningEvaluationDateResolver resolver(Clock clock) {
        return new OperationalScreeningEvaluationDateResolver(
                tradingCalendar, recoveryService, clock);
    }

    private DailyPriceFinalizationExecutionSnapshot snapshot(
            LocalDate date,
            DailyPriceFinalizationExecutionStatus status,
            boolean ready
    ) {
        return new DailyPriceFinalizationExecutionSnapshot(
                1L, date, status, ready, 1, Instant.EPOCH, null,
                0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }
}
