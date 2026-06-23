package com.stockapp.domain.signal;

import com.stockapp.domain.notification.ExpoPushService;
import com.stockapp.domain.notification.NotificationToken;
import com.stockapp.domain.notification.NotificationTokenRepository;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockPrice;
import com.stockapp.domain.stock.StockPriceRepository;
import com.stockapp.domain.stock.StockRepository;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeSignalService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final SignalRepository signalRepository;
    private final NotificationTokenRepository notificationTokenRepository;
    private final ExpoPushService expoPushService;

    private static final double VOLUME_SPIKE_RATE = 2.0;
    private static final int DUPLICATE_CHECK_MINUTES = 30;

    @Transactional
    public void analyzeVolumeSpike(KisRealtimeTradePrice tradePrice) {
        log.info(
                "실시간 거래량 분석 시작 - stockCode: {}, accumulatedVolume: {}",
                tradePrice.getStockCode(),
                tradePrice.getAccumulatedVolume()
        );

        List<StockPrice> prices =
                stockPriceRepository.findTop5ByStockCodeOrderByCollectedAtDesc(tradePrice.getStockCode());

        if (prices.size() < 5) {
            log.info(
                    "실시간 거래량 분석 중단 - 최근 가격 데이터 부족, stockCode: {}, count: {}",
                    tradePrice.getStockCode(),
                    prices.size()
            );
            return;
        }

        double averageVolume = prices.stream()
                .mapToLong(StockPrice::getVolume)
                .average()
                .orElse(0);

        analyzeVolumeSpike(tradePrice, (long) averageVolume);
    }

    @Transactional
    public void analyzeVolumeSpike(
            KisRealtimeTradePrice tradePrice,
            long averageVolume
    ) {
        Stock stock = stockRepository.findByStockCode(tradePrice.getStockCode())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목코드입니다: " + tradePrice.getStockCode()));

        if (averageVolume <= 0) {
            log.info("실시간 거래량 분석 중단 - 평균 거래량 0 이하, stockCode: {}", tradePrice.getStockCode());
            return;
        }

        long currentVolume = tradePrice.getAccumulatedVolume();
        double changeRate = (double) currentVolume / averageVolume;

        log.info(
                "실시간 거래량 분석 결과 - stockCode: {}, averageVolume: {}, currentVolume: {}, changeRate: {}",
                stock.getStockCode(),
                averageVolume,
                currentVolume,
                changeRate
        );

        if (changeRate < VOLUME_SPIKE_RATE) {
            return;
        }

        boolean alreadyExists = signalRepository.existsByStockAndSignalTypeAndDetectedAtAfter(
                stock,
                SignalType.VOLUME_SPIKE,
                LocalDateTime.now().minusMinutes(DUPLICATE_CHECK_MINUTES)
        );

        if (alreadyExists) {
            log.info("실시간 Signal 생성 중단 - 최근 30분 내 중복 Signal 존재, stockCode: {}", stock.getStockCode());
            return;
        }

        Signal signal = Signal.createVolumeSpike(
                stock,
                averageVolume,
                currentVolume,
                changeRate
        );

        signalRepository.save(signal);

        log.info(
                "실시간 거래량 급증 Signal 생성 완료 - stockCode: {}, signalId: {}, changeRate: {}",
                stock.getStockCode(),
                signal.getId(),
                changeRate
        );

        sendSignalPushToAllUsers(signal);
    }

    private void sendSignalPushToAllUsers(Signal signal) {
        String title = "Stock Signal";
        String body = signal.getStock().getStockName() + " - " + signal.getMessage();

        List<NotificationToken> tokens = notificationTokenRepository.findAll();

        log.info(
                "실시간 Signal Push 발송 시작 - stockCode: {}, tokenCount: {}",
                signal.getStock().getStockCode(),
                tokens.size()
        );

        tokens.forEach(notificationToken ->
                expoPushService.sendPush(
                        notificationToken.getToken(),
                        title,
                        body
                )
        );
    }
}