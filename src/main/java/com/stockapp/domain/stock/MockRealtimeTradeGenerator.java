package com.stockapp.domain.stock;

import com.stockapp.domain.signal.RealtimeSignalService;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock/realtime-trades")
public class MockRealtimeTradeGenerator {

    private final RealtimeSignalService realtimeSignalService;

    @PostMapping("/volume-spike/{stockCode}")
    public String generateVolumeSpike(@PathVariable String stockCode) {
        KisRealtimeTradePrice tradePrice = KisRealtimeTradePrice.builder()
                .stockCode(stockCode)
                .currentPrice(70000)
                .accumulatedVolume(3_000_000)
                .tradeDateTime(LocalDateTime.now())
                .build();

        long averageVolume = 1_000_000;

        realtimeSignalService.analyzeVolumeSpike(tradePrice, averageVolume);

        return "Mock 실시간 거래량 급증 테스트 완료: " + stockCode;
    }
}