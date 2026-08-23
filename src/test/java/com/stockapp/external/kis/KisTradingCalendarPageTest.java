package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTradingDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KisTradingCalendarPageTest {

    @Test
    void preservesResponseBoundariesAndDiagnosesAscendingOrder() {
        KisTradingCalendarPage page = page("2026-08-20", "2026-08-21", "2026-08-22");

        assertThat(page.firstDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(page.lastDate()).isEqualTo(LocalDate.of(2026, 8, 22));
        assertThat(page.minDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(page.maxDate()).isEqualTo(LocalDate.of(2026, 8, 22));
        assertThat(page.responseOrder()).isEqualTo(
                KisTradingCalendarResponseOrder.ASCENDING);
    }

    @Test
    void diagnosesDescendingAndMixedResponseOrder() {
        assertThat(page("2026-08-22", "2026-08-21", "2026-08-20").responseOrder())
                .isEqualTo(KisTradingCalendarResponseOrder.DESCENDING);
        assertThat(page("2026-08-20", "2026-08-22", "2026-08-21").responseOrder())
                .isEqualTo(KisTradingCalendarResponseOrder.MIXED);
    }

    private KisTradingCalendarPage page(String... dates) {
        List<KisTradingDay> rows = java.util.Arrays.stream(dates)
                .map(LocalDate::parse)
                .map(date -> new KisTradingDay(date, true))
                .toList();
        return KisTradingCalendarPage.from(
                1, 1, "M", true, true, rows, Set.of("bass_dt"));
    }
}
