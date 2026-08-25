package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyHistoryMissingRange;
import com.stockapp.domain.stock.dto.KisDailyPriceRequestChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisDailyPriceRequestChunkPlannerTest {

    private final KrxTradingCalendar tradingCalendar = mock(KrxTradingCalendar.class);
    private final KisDailyPriceRequestChunkPlanner planner =
            new KisDailyPriceRequestChunkPlanner(tradingCalendar);

    @ParameterizedTest
    @CsvSource({
            "1,1",
            "99,1",
            "100,1",
            "101,2",
            "200,2",
            "201,3"
    })
    void splitsByTheOfficialOneHundredTradingDayLimit(
            int tradingDayCount,
            int expectedChunkCount) {
        List<LocalDate> tradingDays = weekdayTradingDays(
                LocalDate.of(2025, 1, 2),
                tradingDayCount);
        DailyHistoryMissingRange range = rangeOf(tradingDays);
        when(tradingCalendar.tradingDaysBetween(
                range.startDate(), range.endDate()))
                .thenReturn(tradingDays);

        List<KisDailyPriceRequestChunk> chunks = planner.plan(range);

        assertThat(chunks).hasSize(expectedChunkCount);
        assertThat(chunks.getFirst().startDate()).isEqualTo(tradingDays.getFirst());
        assertThat(chunks.getLast().endDate()).isEqualTo(tradingDays.getLast());
        assertCompleteCoverage(chunks, tradingDays);
    }

    @Test
    void usesTradingDayIndexesAcrossAWeekendAndHolidayAtTheChunkBoundary() {
        List<LocalDate> tradingDays = weekdayTradingDaysEndingOn(
                LocalDate.of(2025, 8, 14),
                100);
        LocalDate nextTradingDayAfterHolidayAndWeekend =
                LocalDate.of(2025, 8, 18);
        tradingDays = new ArrayList<>(tradingDays);
        tradingDays.add(nextTradingDayAfterHolidayAndWeekend);
        DailyHistoryMissingRange range = rangeOf(tradingDays);
        when(tradingCalendar.tradingDaysBetween(
                range.startDate(), range.endDate()))
                .thenReturn(tradingDays);

        List<KisDailyPriceRequestChunk> chunks = planner.plan(range);

        assertThat(chunks).containsExactly(
                new KisDailyPriceRequestChunk(
                        tradingDays.getFirst(),
                        LocalDate.of(2025, 8, 14)),
                new KisDailyPriceRequestChunk(
                        nextTradingDayAfterHolidayAndWeekend,
                        nextTradingDayAfterHolidayAndWeekend));
        assertCompleteCoverage(chunks, tradingDays);
    }

    @Test
    void chunkCountDependsOnTradingDaysNotCalendarDays() {
        List<LocalDate> tradingDays = List.of(
                LocalDate.of(2025, 5, 2),
                LocalDate.of(2025, 5, 7),
                LocalDate.of(2025, 5, 8));
        DailyHistoryMissingRange range = rangeOf(tradingDays);
        when(tradingCalendar.tradingDaysBetween(
                range.startDate(), range.endDate()))
                .thenReturn(tradingDays);

        assertThat(planner.plan(range)).containsExactly(
                new KisDailyPriceRequestChunk(
                        LocalDate.of(2025, 5, 2),
                        LocalDate.of(2025, 5, 8)));
    }

    @Test
    void propagatesCalendarCoverageFailure() {
        DailyHistoryMissingRange range = new DailyHistoryMissingRange(
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 6, 30));
        TradingCalendarUnavailableException failure =
                new TradingCalendarUnavailableException(
                        range.startDate(), "coverage gap");
        when(tradingCalendar.tradingDaysBetween(
                range.startDate(), range.endDate()))
                .thenThrow(failure);

        assertThatExceptionOfType(TradingCalendarUnavailableException.class)
                .isThrownBy(() -> planner.plan(range))
                .isSameAs(failure);
    }

    @Test
    void rejectsARangeWithoutTradingDays() {
        DailyHistoryMissingRange range = new DailyHistoryMissingRange(
                LocalDate.of(2025, 5, 3),
                LocalDate.of(2025, 5, 4));
        when(tradingCalendar.tradingDaysBetween(
                range.startDate(), range.endDate()))
                .thenReturn(List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> planner.plan(range))
                .withMessage("range must contain at least one KRX trading day");
    }

    @Test
    void rejectsNullRangeBeforeUsingTheCalendar() {
        assertThatNullPointerException()
                .isThrownBy(() -> planner.plan(null))
                .withMessage("range is required");
        verifyNoInteractions(tradingCalendar);
    }

    @Test
    void returnsAnImmutableResult() {
        List<LocalDate> tradingDays = weekdayTradingDays(
                LocalDate.of(2025, 1, 2),
                1);
        DailyHistoryMissingRange range = rangeOf(tradingDays);
        when(tradingCalendar.tradingDaysBetween(
                range.startDate(), range.endDate()))
                .thenReturn(tradingDays);

        List<KisDailyPriceRequestChunk> chunks = planner.plan(range);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(chunks::clear);
        verify(tradingCalendar).tradingDaysBetween(
                range.startDate(), range.endDate());
    }

    @Test
    void coversEveryTradingDayExactlyOnceInOldestToNewestOrder() {
        List<LocalDate> tradingDays = weekdayTradingDays(
                LocalDate.of(2025, 1, 2),
                230);
        DailyHistoryMissingRange range = rangeOf(tradingDays);
        when(tradingCalendar.tradingDaysBetween(
                range.startDate(), range.endDate()))
                .thenReturn(tradingDays);

        List<KisDailyPriceRequestChunk> chunks = planner.plan(range);

        assertThat(chunks).hasSize(3);
        assertCompleteCoverage(chunks, tradingDays);
    }

    @Test
    void chunkValueRejectsNullAndReversedDates() {
        LocalDate date = LocalDate.of(2025, 1, 2);

        assertThatNullPointerException()
                .isThrownBy(() -> new KisDailyPriceRequestChunk(null, date))
                .withMessage("startDate is required");
        assertThatNullPointerException()
                .isThrownBy(() -> new KisDailyPriceRequestChunk(date, null))
                .withMessage("endDate is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisDailyPriceRequestChunk(
                        date, date.minusDays(1)))
                .withMessage("endDate must be on or after startDate");
    }

    private static void assertCompleteCoverage(
            List<KisDailyPriceRequestChunk> chunks,
            List<LocalDate> tradingDays) {
        List<LocalDate> covered = chunks.stream()
                .flatMap(chunk -> tradingDays.stream()
                        .filter(date -> !date.isBefore(chunk.startDate()))
                        .filter(date -> !date.isAfter(chunk.endDate())))
                .toList();

        assertThat(covered).containsExactlyElementsOf(tradingDays);
        assertThat(covered).doesNotHaveDuplicates();
        assertThat(chunks).allSatisfy(chunk -> {
            long count = tradingDays.stream()
                    .filter(date -> !date.isBefore(chunk.startDate()))
                    .filter(date -> !date.isAfter(chunk.endDate()))
                    .count();
            assertThat(count).isBetween(1L, 100L);
        });
    }

    private static DailyHistoryMissingRange rangeOf(
            List<LocalDate> tradingDays) {
        return new DailyHistoryMissingRange(
                tradingDays.getFirst(),
                tradingDays.getLast());
    }

    private static List<LocalDate> weekdayTradingDays(
            LocalDate startDate,
            int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = startDate;
        while (dates.size() < count) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return List.copyOf(dates);
    }

    private static List<LocalDate> weekdayTradingDaysEndingOn(
            LocalDate endDate,
            int count) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate date = endDate;
        while (dates.size() < count) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
            date = date.minusDays(1);
        }
        return dates.reversed();
    }
}
