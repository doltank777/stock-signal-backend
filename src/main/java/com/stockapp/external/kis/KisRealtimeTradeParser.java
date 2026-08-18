package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class KisRealtimeTradeParser {

    private static final String TR_ID_REALTIME_PRICE = "H0STCNT0";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public KisRealtimeTradeParser(Clock clock) {
        this.clock = clock;
    }

    public boolean supports(String payload) {
        return payload != null && payload.startsWith("0|" + TR_ID_REALTIME_PRICE + "|");
    }

    public KisRealtimeTradePrice parse(String payload) {
        String[] sections = payload.split("\\|");

        if (sections.length < 4) {
            throw new IllegalArgumentException("KIS 실시간 체결 메시지 형식이 올바르지 않습니다: " + payload);
        }

        String data = sections[3];
        String[] values = data.split("\\^");

        String stockCode = values[0];
        String tradeTime = values[1];
        long currentPrice = Long.parseLong(values[2]);
        long accumulatedVolume = Long.parseLong(values[13]);

        LocalDateTime tradeDateTime = LocalDateTime.of(
                LocalDate.now(clock.withZone(KOREA_ZONE)),
                LocalTime.of(
                        Integer.parseInt(tradeTime.substring(0, 2)),
                        Integer.parseInt(tradeTime.substring(2, 4)),
                        Integer.parseInt(tradeTime.substring(4, 6))
                )
        );

        return KisRealtimeTradePrice.builder()
                .stockCode(stockCode)
                .currentPrice(currentPrice)
                .accumulatedVolume(accumulatedVolume)
                .tradeDateTime(tradeDateTime)
                .build();
    }
}
