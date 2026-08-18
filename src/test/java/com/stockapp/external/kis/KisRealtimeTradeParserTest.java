package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class KisRealtimeTradeParserTest {

    @Test
    void usesKoreaDateFromInjectedClock() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-17T15:30:00Z"),
                ZoneOffset.UTC);
        KisRealtimeTradeParser parser = new KisRealtimeTradeParser(clock);
        String payload = "0|H0STCNT0|001|005930^101530^71000"
                + "^0^0^0^0^0^0^0^0^0^0^2500000";

        KisRealtimeTradePrice trade = parser.parse(payload);

        assertThat(trade.getStockCode()).isEqualTo("005930");
        assertThat(trade.getCurrentPrice()).isEqualTo(71_000L);
        assertThat(trade.getAccumulatedVolume()).isEqualTo(2_500_000L);
        assertThat(trade.getTradeDateTime()).isEqualTo(
                LocalDateTime.of(2026, 8, 18, 10, 15, 30));
    }
}
