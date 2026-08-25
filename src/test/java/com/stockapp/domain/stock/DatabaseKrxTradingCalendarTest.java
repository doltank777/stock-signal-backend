package com.stockapp.domain.stock;

import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseKrxTradingCalendarTest {

    private final KrxTradingDayRepository repository =
            mock(KrxTradingDayRepository.class);
    private final DatabaseKrxTradingCalendar calendar =
            new DatabaseKrxTradingCalendar(repository);

    @Test
    void distinguishesTradingClosedAndUnavailableDates() {
        LocalDate trading = LocalDate.of(2026, 8, 14);
        LocalDate closed = LocalDate.of(2026, 8, 15);
        when(repository.findById(trading)).thenReturn(Optional.of(
                day(trading, true)));
        when(repository.findById(closed)).thenReturn(Optional.of(
                day(closed, false)));

        assertThat(calendar.isTradingDay(trading)).isTrue();
        assertThat(calendar.isTradingDay(closed)).isFalse();
        assertThatThrownBy(() -> calendar.isTradingDay(
                LocalDate.of(2026, 8, 16)))
                .isInstanceOf(TradingCalendarUnavailableException.class);
    }

    @Test
    void findsPreviousTradingDayWithOneRepositoryQuery() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        LocalDate previous = LocalDate.of(2026, 8, 14);
        when(repository
                .findFirstByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        date)).thenReturn(Optional.of(day(previous, true)));
        when(repository.countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                previous, date)).thenReturn(3L);
        assertThat(calendar.previousTradingDay(date)).isEqualTo(previous);
        assertThatThrownBy(() -> calendar.previousTradingDay(
                LocalDate.of(2020, 1, 1)))
                .isInstanceOf(TradingCalendarUnavailableException.class);
    }

    @Test
    void failsClosedWhenPreviousTradingDayRangeHasCoverageGap() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        LocalDate previous = LocalDate.of(2026, 8, 14);
        when(repository
                .findFirstByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        date)).thenReturn(Optional.of(day(previous, true)));
        when(repository.countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                previous, date)).thenReturn(2L);

        assertThatThrownBy(() -> calendar.previousTradingDay(date))
                .isInstanceOf(TradingCalendarUnavailableException.class)
                .hasMessageContaining("missing dates");
    }

    @Test
    void zeroPreviousTradingDaysReturnsEmptyWithoutRepositoryAccess() {
        assertThat(calendar.previousTradingDays(
                LocalDate.of(2026, 8, 24), 0)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNegativePreviousTradingDayCountBeforeRepositoryAccess() {
        assertThatThrownBy(() -> calendar.previousTradingDays(
                LocalDate.of(2026, 8, 24), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
        verifyNoInteractions(repository);
    }

    @Test
    void returnsExactPreviousTradingDaysOldestFirst() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        List<KrxTradingDay> descending = List.of(
                day(LocalDate.of(2026, 8, 21), true),
                day(LocalDate.of(2026, 8, 20), true),
                day(LocalDate.of(2026, 8, 19), true));
        when(repository
                .findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        date, PageRequest.of(0, 3)))
                .thenReturn(descending);
        when(repository.countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                LocalDate.of(2026, 8, 19), date)).thenReturn(5L);

        List<LocalDate> result = calendar.previousTradingDays(date, 3);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21));
        assertThatThrownBy(() -> result.add(LocalDate.of(2026, 8, 18)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void crossesWeekendAndHolidayRowsWithoutCountingThem() {
        LocalDate monday = LocalDate.of(2026, 8, 24);
        LocalDate friday = LocalDate.of(2026, 8, 21);
        when(repository
                .findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        monday, PageRequest.of(0, 1)))
                .thenReturn(List.of(day(friday, true)));
        when(repository.countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                friday, monday)).thenReturn(3L);

        assertThat(calendar.previousTradingDays(monday, 1))
                .containsExactly(friday);
    }

    @Test
    void allowsClosedEvaluationDateAndReturnsTradingDaysBeforeIt() {
        LocalDate closedDate = LocalDate.of(2026, 8, 23);
        LocalDate friday = LocalDate.of(2026, 8, 21);
        when(repository
                .findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        closedDate, PageRequest.of(0, 1)))
                .thenReturn(List.of(day(friday, true)));
        when(repository.countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                friday, closedDate)).thenReturn(2L);

        assertThat(calendar.previousTradingDays(closedDate, 1))
                .containsExactly(friday);
    }

    @Test
    void failsClosedWhenFewerTradingDaysThanRequestedAreAvailable() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(repository
                .findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        date, PageRequest.of(0, 3)))
                .thenReturn(List.of(
                        day(LocalDate.of(2026, 8, 21), true),
                        day(LocalDate.of(2026, 8, 20), true)));

        assertThatThrownBy(() -> calendar.previousTradingDays(date, 3))
                .isInstanceOf(TradingCalendarUnavailableException.class)
                .hasMessageContaining("outside synchronized coverage");
    }

    @Test
    void failsClosedWhenRequiredCalendarRangeContainsMissingDateRows() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        LocalDate oldest = LocalDate.of(2026, 8, 19);
        when(repository
                .findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
                        date, PageRequest.of(0, 2)))
                .thenReturn(List.of(
                        day(LocalDate.of(2026, 8, 21), true),
                        day(oldest, true)));
        when(repository.countByTradeDateGreaterThanEqualAndTradeDateLessThan(
                oldest, date)).thenReturn(4L);

        assertThatThrownBy(() -> calendar.previousTradingDays(date, 2))
                .isInstanceOf(TradingCalendarUnavailableException.class)
                .hasMessageContaining("missing dates");
    }

    @Test
    void returnsTradingDaysInInclusiveRangeOldestFirst() {
        LocalDate start = LocalDate.of(2026, 8, 21);
        LocalDate end = LocalDate.of(2026, 8, 24);
        when(repository.countByTradeDateBetween(start, end)).thenReturn(4L);
        when(repository
                .findByTradeDateBetweenAndTradingDayTrueOrderByTradeDateAsc(
                        start, end))
                .thenReturn(List.of(day(start, true), day(end, true)));

        assertThat(calendar.tradingDaysBetween(start, end))
                .containsExactly(start, end);
    }

    @Test
    void failsClosedWhenTradingDayRangeHasCalendarCoverageGap() {
        LocalDate start = LocalDate.of(2026, 8, 19);
        LocalDate end = LocalDate.of(2026, 8, 21);
        when(repository.countByTradeDateBetween(start, end)).thenReturn(2L);

        assertThatThrownBy(() -> calendar.tradingDaysBetween(start, end))
                .isInstanceOf(TradingCalendarUnavailableException.class)
                .hasMessageContaining("missing dates");
    }

    @Test
    void rejectsInvalidTradingDayRangeBeforeRepositoryAccess() {
        assertThatThrownBy(() -> calendar.tradingDaysBetween(
                LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on or after");
        verifyNoInteractions(repository);
    }

    private KrxTradingDay day(LocalDate date, boolean trading) {
        return KrxTradingDay.create(date, trading, "KIS", Instant.EPOCH);
    }
}
