package com.stockapp.domain.signal;

import com.stockapp.domain.favorite.Favorite;
import com.stockapp.domain.favorite.FavoriteRepository;
import com.stockapp.domain.notification.ExpoPushService;
import com.stockapp.domain.notification.NotificationToken;
import com.stockapp.domain.notification.NotificationTokenRepository;
import com.stockapp.domain.signal.dto.SignalResponse;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockPrice;
import com.stockapp.domain.stock.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignalService {

    private final StockPriceRepository stockPriceRepository;
    private final SignalRepository signalRepository;
    private final NotificationTokenRepository notificationTokenRepository;
    private final ExpoPushService expoPushService;
    private final FavoriteRepository favoriteRepository;

    private static final double VOLUME_SPIKE_RATE = 2.0;
    private static final int RECENT_PRICE_LIMIT = 5;
    private static final int MOVING_AVERAGE_LIMIT = 6;
    private static final int DUPLICATE_CHECK_MINUTES = 30;

    @Transactional
    public void analyzeVolumeSpike(Stock stock) {
        List<StockPrice> prices = stockPriceRepository.findTop5ByStockCodeOrderByCollectedAtDesc(stock.getStockCode());

        if (prices.size() < RECENT_PRICE_LIMIT) {
            return;
        }

        StockPrice latestPrice = prices.getFirst();
        long currentVolume = latestPrice.getVolume();

        double averageVolume = prices.stream()
                .skip(1)
                .mapToLong(StockPrice::getVolume)
                .average()
                .orElse(0);

        if (averageVolume <= 0) {
            return;
        }

        double changeRate = currentVolume / averageVolume;

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
                (long) averageVolume,
                currentVolume,
                changeRate
        );

        signalRepository.save(signal);
        sendSignalPush(signal);
    }

    @Transactional
    public void analyzeMovingAverageBreakout(Stock stock) {
        List<StockPrice> prices = stockPriceRepository.findTop6ByStockCodeOrderByCollectedAtDesc(stock.getStockCode());

        if (prices.size() < MOVING_AVERAGE_LIMIT) {
            return;
        }

        StockPrice latestPrice = prices.getFirst();
        long currentPrice = latestPrice.getCurrentPrice();

        double averagePrice = prices.stream()
                .skip(1)
                .mapToLong(StockPrice::getCurrentPrice)
                .average()
                .orElse(0);

        if (averagePrice <= 0) {
            return;
        }

        boolean isBreakout = currentPrice > averagePrice;

        if (!isBreakout) {
            return;
        }

        double changeRate = currentPrice / averagePrice;

        boolean alreadyExists = signalRepository.existsByStockAndSignalTypeAndDetectedAtAfter(
                stock,
                SignalType.MOVING_AVERAGE_BREAKOUT,
                LocalDateTime.now().minusMinutes(DUPLICATE_CHECK_MINUTES)
        );

        if (alreadyExists) {
            return;
        }

        Signal signal = Signal.createMovingAverageBreakout(
                stock,
                (long) averagePrice,
                currentPrice,
                changeRate
        );

        signalRepository.save(signal);
        sendSignalPush(signal);
    }

    public List<SignalResponse> getSignals() {
        return signalRepository.findAllWithStockOrderByDetectedAtDesc()
                .stream()
                .limit(50)
                .map(SignalResponse::from)
                .toList();
    }


    private void sendSignalPush(Signal signal) {

        String title = "Stock Signal";
        String body = signal.getStock().getStockName() + " - " + signal.getMessage();

        List<Favorite> favorites =
                favoriteRepository.findByStock(signal.getStock());

        favorites.stream()
                .map(Favorite::getUser)
                .distinct()
                .forEach(user -> {

                    List<NotificationToken> tokens =
                            notificationTokenRepository.findByUser(user);

                    tokens.forEach(notificationToken ->
                            expoPushService.sendPush(
                                    notificationToken.getToken(),
                                    title,
                                    body
                            )
                    );
                });
    }
}