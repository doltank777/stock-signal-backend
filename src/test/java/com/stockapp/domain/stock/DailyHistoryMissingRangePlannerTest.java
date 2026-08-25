package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyHistoryMissingRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyHistoryMissingRangePlannerTest {

    @Mock
    private KrxTradingCalendar calendar;

    private DailyHistoryMissingRangePlanner planner;

    @BeforeEach
    void setUp() {
        planner = new DailyHistoryMissingRangePlanner(calendar);
    }

    @Test
    void returnsEmptyWithoutCalendarQuery() {
        assertThat(planner.plan(List.of())).isEmpty();
        verifyNoInteractions(calendar);
    }

    @Test
    void plansSingleDateAsInclusiveRange() {
        when(calendar.tradingDaysBetween(date(21), date(21)))
                .thenReturn(dates(21));

        assertThat(planner.plan(dates(21)))
                .containsExactly(range(21, 21));
    }

    @Test
    void combinesConsecutiveTradingDaysIntoOneRange() {
        when(calendar.tradingDaysBetween(date(10), date(14)))
                .thenReturn(dates(10, 11, 12, 13, 14));

        assertThat(planner.plan(dates(10, 11, 12, 13, 14)))
                .containsExactly(range(10, 14));
    }

    @Test
    void combinesFridayAndMondayAcrossClosedWeekend() {
        when(calendar.tradingDaysBetween(date(21), date(24)))
                .thenReturn(dates(21, 24));

        assertThat(planner.plan(dates(21, 24)))
                .containsExactly(range(21, 24));
    }

    @Test
    void combinesTradingDaysAcrossClosedHoliday() {
        when(calendar.tradingDaysBetween(date(14), date(18)))
                .thenReturn(dates(14, 18));

        assertThat(planner.plan(dates(14, 18)))
                .containsExactly(range(14, 18));
    }

    @Test
    void splitsRangesAtExistingTradingDay() {
        when(calendar.tradingDaysBetween(date(19), date(21)))
                .thenReturn(dates(19, 20, 21));

        assertThat(planner.plan(dates(19, 21)))
                .containsExactly(range(19, 19), range(21, 21));
    }

    @Test
    void plansMultipleRangesOldestFirst() {
        when(calendar.tradingDaysBetween(date(10), date(21)))
                .thenReturn(dates(10, 11, 12, 13, 14, 18, 19, 20, 21));

        assertThat(planner.plan(dates(10, 11, 12, 18, 19, 21)))
                .containsExactly(
                        range(10, 12), range(18, 19), range(21, 21));
    }

    @Test
    void normalizesUnsortedDuplicateInput() {
        when(calendar.tradingDaysBetween(date(19), date(21)))
                .thenReturn(dates(19, 20, 21));

        assertThat(planner.plan(dates(21, 19, 20, 19)))
                .containsExactly(range(19, 21));
    }

    @Test
    void rejectsNullInputsBeforeCalendarQuery() {
        assertThatThrownBy(() -> planner.plan(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("missingTradingDates is required");
        assertThatThrownBy(() -> planner.plan(
                java.util.Arrays.asList(date(19), null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("missingTradingDates must not contain null");
        verifyNoInteractions(calendar);
    }

    @Test
    void rejectsNonTradingMissingDate() {
        when(calendar.tradingDaysBetween(date(19), date(21)))
                .thenReturn(dates(19, 21));

        assertThatThrownBy(() -> planner.plan(dates(19, 20, 21)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KRX trading days");
    }

    @Test
    void propagatesCalendarCoverageFailure() {
        when(calendar.tradingDaysBetween(date(19), date(21)))
                .thenThrow(new TradingCalendarUnavailableException(
                        date(19), "calendar coverage contains missing dates"));

        assertThatThrownBy(() -> planner.plan(dates(19, 21)))
                .isInstanceOf(TradingCalendarUnavailableException.class)
                .hasMessageContaining("missing dates");
    }

    @Test
    void returnsImmutableRanges() {
        when(calendar.tradingDaysBetween(date(21), date(21)))
                .thenReturn(dates(21));
        List<DailyHistoryMissingRange> result = planner.plan(dates(21));

        assertThatThrownBy(() -> result.add(range(22, 22)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rangeRejectsNullAndReversedDates() {
        assertThatThrownBy(() -> new DailyHistoryMissingRange(null, date(21)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DailyHistoryMissingRange(date(21), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> range(21, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on or after");
    }

    private DailyHistoryMissingRange range(int startDay, int endDay) {
        return new DailyHistoryMissingRange(date(startDay), date(endDay));
    }

    private List<LocalDate> dates(int... days) {
        return java.util.Arrays.stream(days).mapToObj(this::date).toList();
    }

    private LocalDate date(int day) {
        return LocalDate.of(2026, 8, day);
    }
}
