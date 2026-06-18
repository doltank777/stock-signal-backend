package com.stockapp.domain.signal;

import com.stockapp.domain.notification.ExpoPushService;
import com.stockapp.domain.notification.NotificationToken;
import com.stockapp.domain.notification.NotificationTokenRepository;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RealtimeSignalService {

    private final StockRepository stockRepository;
    private final SignalRepository signalRepository;
    private final NotificationTokenRepository notificationTokenRepository;
    private final ExpoPushService expoPushService;

    private static final double VOLUME_SPIKE_RATE = 2.0;
    private static final int DUPLICATE_CHECK_MINUTES = 30;

    @Transactional
    public void analyzeVolumeSpike(
            KisRealtimeTradePrice tradePrice,
            long averageVolume
    ) {
        Stock stock = stockRepository.findByStockCode(tradePrice.getStockCode())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목코드입니다: " + tradePrice.getStockCode()));

        if (averageVolume <= 0) {
            return;
        }

        long currentVolume = tradePrice.getAccumulatedVolume();
        double changeRate = (double) currentVolume / averageVolume;

        if (changeRate < VOLUME_SPIKE_RATE) {
            return;
        }

        boolean alreadyExists = signalRepository.existsByStockAndSignalTypeAndDetectedAtAfter(
                stock,
                SignalType.VOLUME_SPIKE,
                LocalDateTime.now().minusMinutes(DUPLICATE_CHECK_MINUTES)
        );

        if (alreadyExists) {
            return;
        }

        Signal signal = Signal.createVolumeSpike(
                stock,
                averageVolume,
                currentVolume,
                changeRate
        );

        signalRepository.save(signal);
        sendSignalPushToAllUsers(signal);
    }

    private void sendSignalPushToAllUsers(Signal signal) {
        String title = "Stock Signal";
        String body = signal.getStock().getStockName() + " - " + signal.getMessage();

        List<NotificationToken> tokens = notificationTokenRepository.findAll();

        tokens.forEach(notificationToken ->
                expoPushService.sendPush(
                        notificationToken.getToken(),
                        title,
                        body
                )
        );
    }
}