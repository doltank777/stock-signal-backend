package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisDailyPriceProbeRunnerTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 20);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T07:20:03Z"), ZoneOffset.UTC);

    @Test
    void returnsTargetDateRowWithOneApiCall() {
        KisDailyPriceClient client = mock(KisDailyPriceClient.class);
        KisDailyPrice row = dailyPrice(TARGET_DATE);
        when(client.getDailyPrices("005930", TARGET_DATE, TARGET_DATE))
                .thenReturn(List.of(row));

        KisDailyPriceProbeResult result = runner(client, "005930", "2026-08-20")
                .execute();

        assertThat(result.rowFound()).isTrue();
        assertThat(result.responseRowCount()).isEqualTo(1);
        assertThat(result.row()).isSameAs(row);
        verify(client).getDailyPrices("005930", TARGET_DATE, TARGET_DATE);
    }

    @Test
    void reportsSuccessfulResponseWithoutTargetDateRow() {
        KisDailyPriceClient client = mock(KisDailyPriceClient.class);
        when(client.getDailyPrices("005930", TARGET_DATE, TARGET_DATE))
                .thenReturn(List.of());

        KisDailyPriceProbeResult result = runner(client, "005930", "2026-08-20")
                .execute();

        assertThat(result.rowFound()).isFalse();
        assertThat(result.responseRowCount()).isZero();
        verify(client).getDailyPrices("005930", TARGET_DATE, TARGET_DATE);
    }

    @Test
    void preservesKisBusinessError() {
        KisDailyPriceClient client = mock(KisDailyPriceClient.class);
        when(client.getDailyPrices("005930", TARGET_DATE, TARGET_DATE))
                .thenThrow(new KisApiException("TEST001", "probe failure"));

        assertThatThrownBy(() -> runner(client, "005930", "2026-08-20").execute())
                .isInstanceOf(KisApiException.class)
                .hasMessageContaining("probe failure");
        verify(client).getDailyPrices("005930", TARGET_DATE, TARGET_DATE);
    }

    @Test
    void invalidInputDoesNotCallKis() {
        KisDailyPriceClient client = mock(KisDailyPriceClient.class);

        assertThatThrownBy(() -> runner(client, " ", "2026-08-20").execute())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stock-code is required");
        verify(client, never()).getDailyPrices(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void analyzesRangeResponseWithOneReadOnlyApiCall() {
        KisDailyPriceClient client = mock(KisDailyPriceClient.class);
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 24);
        when(client.getDailyPrices("005930", startDate, endDate))
                .thenReturn(List.of(dailyPrice(endDate), dailyPrice(startDate)));
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();
        properties.setStockCode("005930");
        properties.setStartDate("2026-08-01");
        properties.setEndDate("2026-08-24");

        KisDailyPriceProbeResult result = new KisDailyPriceProbeRunner(
                properties, client, CLOCK, new KisDailyPriceProbeAnalyzer())
                .execute();

        assertThat(result.targetDate()).isNull();
        assertThat(result.requestedStartDate()).isEqualTo(startDate);
        assertThat(result.requestedEndDate()).isEqualTo(endDate);
        assertThat(result.responseRowCount()).isEqualTo(2);
        assertThat(result.analysis().responseOrder())
                .isEqualTo(KisDailyPriceResponseOrder.DESCENDING);
        assertThat(result.rowFound()).isFalse();
        verify(client).getDailyPrices("005930", startDate, endDate);
    }

    private KisDailyPriceProbeRunner runner(
            KisDailyPriceClient client,
            String stockCode,
            String targetDate
    ) {
        KisDailyPriceProbeProperties properties = new KisDailyPriceProbeProperties();
        properties.setStockCode(stockCode);
        properties.setTargetDate(targetDate);
        return new KisDailyPriceProbeRunner(
                properties, client, CLOCK, new KisDailyPriceProbeAnalyzer());
    }

    private KisDailyPrice dailyPrice(LocalDate tradeDate) {
        return KisDailyPrice.builder()
                .tradeDate(tradeDate)
                .openPrice(70000L)
                .highPrice(71500L)
                .lowPrice(69500L)
                .closePrice(71000L)
                .volume(12345678L)
                .build();
    }
}
