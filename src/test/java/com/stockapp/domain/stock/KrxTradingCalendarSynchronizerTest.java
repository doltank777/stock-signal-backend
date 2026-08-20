package com.stockapp.domain.stock;

import com.stockapp.external.kis.KisTradingCalendarClient;
import com.stockapp.external.kis.dto.KisTradingDay;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KrxTradingCalendarSynchronizerTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private final KisTradingCalendarClient client =
            mock(KisTradingCalendarClient.class);
    private final KrxTradingCalendarWriter writer =
            mock(KrxTradingCalendarWriter.class);
    private final KrxTradingCalendarSynchronizer synchronizer =
            new KrxTradingCalendarSynchronizer(client, writer,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void validatesThenDelegatesOneTransactionalWrite() {
        when(client.getTradingDays(BASE_DATE)).thenReturn(List.of(
                new KisTradingDay(BASE_DATE, true),
                new KisTradingDay(BASE_DATE.plusDays(1), false)));
        when(writer.write(any(), any())).thenReturn(
                new KrxTradingCalendarWriter.WriteResult(2, 0, 0));

        var result = synchronizer.synchronize(BASE_DATE);

        assertThat(result.receivedCount()).isEqualTo(2);
        assertThat(result.insertedCount()).isEqualTo(2);
        verify(writer).write(any(), any());
    }

    @Test
    void conflictingDuplicatePreventsAllDatabaseWrites() {
        when(client.getTradingDays(BASE_DATE)).thenReturn(List.of(
                new KisTradingDay(BASE_DATE, true),
                new KisTradingDay(BASE_DATE, false)));

        assertThatThrownBy(() -> synchronizer.synchronize(BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting");
        verify(writer, never()).write(any(), any());
    }

    @Test
    void businessFailureIsNotConvertedToClosedDayOrDatabaseWrite() {
        when(client.getTradingDays(BASE_DATE)).thenThrow(
                new IllegalStateException("KIS unavailable"));
        assertThatThrownBy(() -> synchronizer.synchronize(BASE_DATE))
                .isInstanceOf(IllegalStateException.class);
        verify(writer, never()).write(any(), any());
    }
}
