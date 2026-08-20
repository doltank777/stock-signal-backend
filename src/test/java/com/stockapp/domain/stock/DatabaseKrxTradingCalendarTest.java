package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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

    private KrxTradingDay day(LocalDate date, boolean trading) {
        return KrxTradingDay.create(date, trading, "KIS", Instant.EPOCH);
    }
}
