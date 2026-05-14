package com.stockapp.domain.signal;

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

    private static final double VOLUME_SPIKE_RATE = 2.0; // 평균 거래량의 2배 이상
    private static final int RECENT_PRICE_LIMIT = 5;     // 최근 5개 데이터 기준
    private static final int DUPLICATE_CHECK_MINUTES = 30;

    @Transactional
    public void analyzeVolumeSpike(Stock stock) {

        List<StockPrice> prices =
                stockPriceRepository.findTop5ByStockCodeOrderByCollectedAtDesc(stock.getStockCode());

        if (prices.size() < RECENT_PRICE_LIMIT) {
            return;
        }

        StockPrice latestPrice = prices.get(0);

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
    }
}