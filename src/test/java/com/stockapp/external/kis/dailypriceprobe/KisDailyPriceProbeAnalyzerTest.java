package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.dto.KisDailyPrice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class KisDailyPriceProbeAnalyzerTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 24);

    private final KisDailyPriceProbeAnalyzer analyzer =
            new KisDailyPriceProbeAnalyzer();

    @Test
    void analyzesEmptyResponse() {
        KisDailyPriceProbeAnalysis result = analyze(List.of());

        assertThat(result.responseRowCount()).isZero();
        assertThat(result.responseOrder()).isEqualTo(KisDailyPriceResponseOrder.EMPTY);
        assertThat(result.earliestResponseDate()).isNull();
        assertThat(result.latestResponseDate()).isNull();
        assertThat(result.duplicateDateCount()).isZero();
        assertThat(result.outOfRangeDateCount()).isZero();
        assertThat(result.firstResponseDates()).isEmpty();
        assertThat(result.lastResponseDates()).isEmpty();
    }

    @Test
    void detectsSingleAscendingDescendingAndUnsortedResponses() {
        assertThat(analyze(dates("2026-08-20")).responseOrder())
                .isEqualTo(KisDailyPriceResponseOrder.SINGLE);
        assertThat(analyze(dates("2026-08-20", "2026-08-21", "2026-08-24"))
                .responseOrder()).isEqualTo(KisDailyPriceResponseOrder.ASCENDING);
        assertThat(analyze(dates("2026-08-24", "2026-08-21", "2026-08-20"))
                .responseOrder()).isEqualTo(KisDailyPriceResponseOrder.DESCENDING);
        assertThat(analyze(dates("2026-08-24", "2026-08-20", "2026-08-21"))
                .responseOrder()).isEqualTo(KisDailyPriceResponseOrder.UNSORTED);
    }

    @Test
    void countsDuplicateExtraRowsWithoutChangingOrdering() {
        KisDailyPriceProbeAnalysis result = analyze(dates(
                "2026-08-24", "2026-08-24", "2026-08-21", "2026-08-21",
                "2026-08-20"));

        assertThat(result.duplicateDateCount()).isEqualTo(2);
        assertThat(result.responseOrder())
                .isEqualTo(KisDailyPriceResponseOrder.DESCENDING);
    }

    @Test
    void countsOutOfRangeRowsWithoutFilteringThem() {
        KisDailyPriceProbeAnalysis result = analyzer.analyze(
                prices(dates("2026-07-31", "2026-08-01", "2026-08-24", "2026-08-25")),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 24));

        assertThat(result.responseRowCount()).isEqualTo(4);
        assertThat(result.outOfRangeDateCount()).isEqualTo(2);
    }

    @Test
    void reportsLimitOnlyAtExactlyOneHundredRows() {
        assertThat(analyze(sequentialDates(99)).limitReached()).isFalse();
        assertThat(analyze(sequentialDates(100)).limitReached()).isTrue();
    }

    @Test
    void calculatesMinMaxAndExactBoundaryPresenceIndependentlyOfOrder() {
        KisDailyPriceProbeAnalysis result = analyzer.analyze(
                prices(dates("2026-08-24", "2026-08-10", "2026-08-01")),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 24));

        assertThat(result.earliestResponseDate())
                .isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.latestResponseDate())
                .isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(result.exactStartDatePresent()).isTrue();
        assertThat(result.exactEndDatePresent()).isTrue();
    }

    @Test
    void returnsImmutableFiveDateSamplesInResponseOrder() {
        List<LocalDate> dates = sequentialDates(8);

        KisDailyPriceProbeAnalysis result = analyze(dates);

        assertThat(result.firstResponseDates())
                .containsExactlyElementsOf(dates.subList(0, 5));
        assertThat(result.lastResponseDates())
                .containsExactlyElementsOf(dates.subList(3, 8));
        org.assertj.core.api.Assertions.assertThatExceptionOfType(
                        UnsupportedOperationException.class)
                .isThrownBy(result.firstResponseDates()::clear);
    }

    private KisDailyPriceProbeAnalysis analyze(List<LocalDate> dates) {
        return analyzer.analyze(prices(dates), START_DATE, END_DATE);
    }

    private List<KisDailyPrice> prices(List<LocalDate> dates) {
        return dates.stream().map(this::dailyPrice).toList();
    }

    private List<LocalDate> sequentialDates(int count) {
        return IntStream.range(0, count)
                .mapToObj(START_DATE::plusDays)
                .toList();
    }

    private List<LocalDate> dates(String... dates) {
        return java.util.Arrays.stream(dates).map(LocalDate::parse).toList();
    }

    private KisDailyPrice dailyPrice(LocalDate tradeDate) {
        return KisDailyPrice.builder().tradeDate(tradeDate).build();
    }
}
