package com.stockapp.external.kis.tradingcalendarprobe;

import com.stockapp.external.kis.KisTradingCalendarClient;
import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.KisTradingCalendarFetchResult;
import com.stockapp.external.kis.KisTradingCalendarPage;
import com.stockapp.external.kis.KisTradingCalendarPaginationLimitException;
import com.stockapp.external.kis.KisTradingCalendarResponseOrder;
import com.stockapp.external.kis.dto.KisTradingDay;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisTradingCalendarProbeRunnerTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 20);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T07:20:03Z"), ZoneOffset.UTC);

    @Test
    void calculatesCalendarAndPaginationDiagnosticsFromClientOnly() {
        KisTradingCalendarClient client = mock(KisTradingCalendarClient.class);
        List<KisTradingDay> rows = List.of(
                new KisTradingDay(LocalDate.of(2026, 8, 19), true),
                new KisTradingDay(BASE_DATE, true),
                new KisTradingDay(LocalDate.of(2026, 8, 22), false),
                new KisTradingDay(LocalDate.of(2026, 8, 23), false));
        List<KisTradingCalendarPage> pages = List.of(
                KisTradingCalendarPage.from(1, 2, "", false, false,
                        rows, Set.of("bass_dt", "opnd_yn", "wday_dvsn_cd")));
        when(client.getTradingDaysWithDiagnostics(BASE_DATE))
                .thenReturn(new KisTradingCalendarFetchResult(rows, pages, 2));

        KisTradingCalendarProbeResult result = runner(client).execute();

        assertThat(result.rowCount()).isEqualTo(4);
        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.paginationComplete()).isTrue();
        assertThat(result.firstCollectedDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(result.lastCollectedDate()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(result.minCollectedDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(result.maxCollectedDate()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(result.overallResponseOrder()).isEqualTo(
                KisTradingCalendarResponseOrder.ASCENDING);
        assertThat(result.uniqueDateCount()).isEqualTo(4);
        assertThat(result.duplicateDateCount()).isZero();
        assertThat(result.expectedCalendarDateCount()).isEqualTo(5);
        assertThat(result.baseDatePresent()).isTrue();
        assertThat(result.firstDate()).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(result.lastDate()).isEqualTo(LocalDate.of(2026, 8, 23));
        assertThat(result.tradingDayCount()).isEqualTo(2);
        assertThat(result.closedDayCount()).isEqualTo(2);
        assertThat(result.weekendRowCount()).isEqualTo(2);
        assertThat(result.saturdayRowCount()).isEqualTo(1);
        assertThat(result.sundayRowCount()).isEqualTo(1);
        assertThat(result.containsSaturday()).isTrue();
        assertThat(result.containsSunday()).isTrue();
        assertThat(result.futureDateCount()).isEqualTo(2);
        assertThat(result.pastDateCount()).isEqualTo(1);
        assertThat(result.missingCalendarDateCount()).isEqualTo(1);
        assertThat(result.apiCallCount()).isEqualTo(2);
        assertThat(result.outputFieldNames()).contains("wday_dvsn_cd");
        verify(client).getTradingDaysWithDiagnostics(BASE_DATE);
    }

    @Test
    void rangeModeUsesEndDateAndReportsSourceContinuationSeparately() {
        LocalDate endDate = BASE_DATE.plusDays(2);
        KisTradingCalendarClient client = mock(KisTradingCalendarClient.class);
        List<KisTradingDay> rows = List.of(
                new KisTradingDay(BASE_DATE, true),
                new KisTradingDay(BASE_DATE.plusDays(1), false),
                new KisTradingDay(endDate, false));
        KisTradingCalendarFetchResult fetch =
                new KisTradingCalendarFetchResult(
                        rows, List.of(), 1, BASE_DATE, endDate,
                        true, false, true);
        when(client.getTradingDaysWithDiagnostics(BASE_DATE, endDate))
                .thenReturn(fetch);
        KisTradingCalendarProbeProperties properties =
                new KisTradingCalendarProbeProperties();
        properties.setBaseDate(BASE_DATE.toString());
        properties.setEndDate(endDate.toString());

        KisTradingCalendarProbeResult result =
                new KisTradingCalendarProbeRunner(properties, client, CLOCK)
                        .execute();

        assertThat(result.mode()).isEqualTo("RANGE");
        assertThat(result.requestedEndDate()).isEqualTo(endDate);
        assertThat(result.requestedRangeComplete()).isTrue();
        assertThat(result.paginationComplete()).isFalse();
        assertThat(result.sourceHasMore()).isTrue();
        verify(client).getTradingDaysWithDiagnostics(BASE_DATE, endDate);
    }

    @Test
    void summarizesPartialDescendingRowsWithoutLosingRawBoundaries() {
        KisTradingCalendarClient client = mock(KisTradingCalendarClient.class);
        List<KisTradingDay> rows = List.of(
                day("2026-08-24"), day("2026-08-23"), day("2026-08-22"),
                day("2026-08-21"), day("2026-08-20"));
        KisTradingCalendarFetchResult partial = new KisTradingCalendarFetchResult(
                rows,
                List.of(KisTradingCalendarPage.from(
                        1, 1, "M", true, true, rows, Set.of("bass_dt"))),
                1);
        KisTradingCalendarPaginationLimitException limit =
                new KisTradingCalendarPaginationLimitException(1, partial);
        when(client.getTradingDaysWithDiagnostics(BASE_DATE)).thenThrow(limit);

        assertThatThrownBy(() -> runner(client).execute()).isSameAs(limit);
        assertThat(limit.getPartialFetchResult().days()).containsExactlyElementsOf(rows);

        KisTradingCalendarProbeResult result = runner(client).summarize(
                BASE_DATE, java.time.OffsetDateTime.now(CLOCK), partial, false);
        assertThat(result.paginationComplete()).isFalse();
        assertThat(result.firstCollectedDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(result.lastCollectedDate()).isEqualTo(BASE_DATE);
        assertThat(result.minCollectedDate()).isEqualTo(BASE_DATE);
        assertThat(result.maxCollectedDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(result.overallResponseOrder()).isEqualTo(
                KisTradingCalendarResponseOrder.DESCENDING);
        assertThat(result.baseDatePresent()).isTrue();
    }

    @Test
    void diagnosesMixedOrderDuplicatesGapsAndMissingBaseDate() {
        KisTradingCalendarClient client = mock(KisTradingCalendarClient.class);
        List<KisTradingDay> rows = List.of(
                day("2026-08-19"), day("2026-08-22"), day("2026-08-21"),
                day("2026-08-22"), day("2026-08-23"));
        KisTradingCalendarFetchResult fetch = new KisTradingCalendarFetchResult(
                rows, List.of(), 1);

        KisTradingCalendarProbeResult result = runner(client).summarize(
                BASE_DATE, java.time.OffsetDateTime.now(CLOCK), fetch, false);

        assertThat(result.overallResponseOrder()).isEqualTo(
                KisTradingCalendarResponseOrder.MIXED);
        assertThat(result.uniqueDateCount()).isEqualTo(4);
        assertThat(result.duplicateDateCount()).isEqualTo(1);
        assertThat(result.expectedCalendarDateCount()).isEqualTo(5);
        assertThat(result.missingCalendarDateCount()).isEqualTo(1);
        assertThat(result.baseDatePresent()).isFalse();
    }

    @Test
    void preservesPaperEnvironmentUnsupportedError() {
        KisTradingCalendarClient client = mock(KisTradingCalendarClient.class);
        when(client.getTradingDaysWithDiagnostics(BASE_DATE))
                .thenThrow(new KisApiException(
                        "EGW02006", "모의투자 TR 이 아닙니다."));

        assertThatThrownBy(() -> runner(client).execute())
                .isInstanceOfSatisfying(KisApiException.class, exception -> {
                    assertThat(exception.getMessageCode()).isEqualTo("EGW02006");
                    assertThat(exception.getMessage())
                            .isEqualTo("모의투자 TR 이 아닙니다.");
                });
        verify(client).getTradingDaysWithDiagnostics(BASE_DATE);
    }

    private KisTradingCalendarProbeRunner runner(
            KisTradingCalendarClient client
    ) {
        KisTradingCalendarProbeProperties properties =
                new KisTradingCalendarProbeProperties();
        properties.setBaseDate("2026-08-20");
        properties.setMaxPrintRows(1);
        return new KisTradingCalendarProbeRunner(properties, client, CLOCK);
    }

    private KisTradingDay day(String date) {
        return new KisTradingDay(LocalDate.parse(date), true);
    }
}
