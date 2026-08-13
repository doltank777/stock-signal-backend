package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockDailyPriceService {

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final KisDailyPriceClient kisDailyPriceClient;

    @Transactional
    public StockDailyPriceSaveResult saveDailyPrices(
            String stockCode,
            LocalDate startDate,
            LocalDate endDate) {

        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "종목을 찾을 수 없습니다."));

        List<KisDailyPrice> dailyPrices = kisDailyPriceClient
                .getDailyPrices(stockCode, startDate, endDate);

        Map<LocalDate, KisDailyPrice> uniqueDailyPrices =
                new LinkedHashMap<>();

        dailyPrices.forEach(dailyPrice -> uniqueDailyPrices.putIfAbsent(
                dailyPrice.getTradeDate(),
                dailyPrice));

        List<StockDailyPrice> newDailyPrices = uniqueDailyPrices.values()
                .stream()
                .filter(dailyPrice -> !stockDailyPriceRepository
                        .existsByStockAndTradeDate(
                                stock,
                                dailyPrice.getTradeDate()))
                .map(dailyPrice -> toEntity(stock, dailyPrice))
                .toList();

        if (!newDailyPrices.isEmpty()) {
            stockDailyPriceRepository.saveAll(newDailyPrices);
        }

        int requestedCount = dailyPrices.size();
        int savedCount = newDailyPrices.size();

        return StockDailyPriceSaveResult.builder()
                .requestedCount(requestedCount)
                .savedCount(savedCount)
                .skippedCount(requestedCount - savedCount)
                .build();
    }

    private StockDailyPrice toEntity(
            Stock stock,
            KisDailyPrice dailyPrice) {

        return StockDailyPrice.builder()
                .stock(stock)
                .tradeDate(dailyPrice.getTradeDate())
                .openPrice(dailyPrice.getOpenPrice())
                .highPrice(dailyPrice.getHighPrice())
                .lowPrice(dailyPrice.getLowPrice())
                .closePrice(dailyPrice.getClosePrice())
                .volume(dailyPrice.getVolume())
                .build();
    }
}
