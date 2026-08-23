package com.stockbatch.tradingcalendar;

import com.stockapp.domain.stock.KrxTradingCalendarSynchronizer;
import com.stockapp.domain.stock.dto.KrxTradingCalendarSyncResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradingCalendarSyncRunnerTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2027, 12, 31);

    @Test
    void synchronizesExplicitRangeExactlyOnce() {
        KrxTradingCalendarSynchronizer synchronizer =
                mock(KrxTradingCalendarSynchronizer.class);
        KrxTradingCalendarSyncResult expected =
                new KrxTradingCalendarSyncResult(
                        START_DATE, 730, 730, 0, 0,
                        Instant.parse("2026-08-23T00:00:00Z"),
                        Instant.parse("2026-08-23T00:01:00Z"));
        when(synchronizer.synchronize(START_DATE, END_DATE))
                .thenReturn(expected);

        KrxTradingCalendarSyncResult actual =
                new TradingCalendarSyncRunner(properties(), synchronizer)
                        .execute();

        assertThat(actual).isSameAs(expected);
        verify(synchronizer).synchronize(START_DATE, END_DATE);
    }

    @Test
    void propagatesSynchronizationFailure() {
        KrxTradingCalendarSynchronizer synchronizer =
                mock(KrxTradingCalendarSynchronizer.class);
        IllegalStateException failure =
                new IllegalStateException("KIS unavailable");
        when(synchronizer.synchronize(START_DATE, END_DATE))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                new TradingCalendarSyncRunner(properties(), synchronizer)
                        .execute())
                .isSameAs(failure);
    }

    private TradingCalendarSyncProperties properties() {
        TradingCalendarSyncProperties properties =
                new TradingCalendarSyncProperties();
        properties.setStartDate(START_DATE.toString());
        properties.setEndDate(END_DATE.toString());
        return properties;
    }
}
