package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.dto.KisDailyPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StockDailyPriceWriter {

    private final StockDailyPriceRepository stockDailyPriceRepository;

    @Transactional
    public StockDailyPriceSaveResult write(
            Stock stock,
            List<KisDailyPrice> dailyPrices) {

        Map<LocalDate, KisDailyPrice> uniquePrices = new LinkedHashMap<>();
        dailyPrices.forEach(price -> uniquePrices.putIfAbsent(
                price.getTradeDate(), price));

        Set<LocalDate> existingDates = new HashSet<>();
        if (!uniquePrices.isEmpty()) {
            LocalDate startDate = uniquePrices.keySet().stream().min(LocalDate::compareTo).orElseThrow();
            LocalDate endDate = uniquePrices.keySet().stream().max(LocalDate::compareTo).orElseThrow();
            existingDates.addAll(stockDailyPriceRepository.findTradeDates(
                    stock, startDate, endDate));
        }

        List<StockDailyPrice> newPrices = uniquePrices.values().stream()
                .filter(price -> !existingDates.contains(price.getTradeDate()))
                .map(price -> toEntity(stock, price))
                .toList();
        if (!newPrices.isEmpty()) {
            stockDailyPriceRepository.saveAll(newPrices);
        }

        return StockDailyPriceSaveResult.builder()
                .requestedCount(dailyPrices.size())
                .savedCount(newPrices.size())
                .skippedCount(dailyPrices.size() - newPrices.size())
                .build();
    }

    private StockDailyPrice toEntity(Stock stock, KisDailyPrice price) {
        return StockDailyPrice.builder()
                .stock(stock)
                .tradeDate(price.getTradeDate())
                .openPrice(price.getOpenPrice())
                .highPrice(price.getHighPrice())
                .lowPrice(price.getLowPrice())
                .closePrice(price.getClosePrice())
                .volume(price.getVolume())
                .build();
    }
}
