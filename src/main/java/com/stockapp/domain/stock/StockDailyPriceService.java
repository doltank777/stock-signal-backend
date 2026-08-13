package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockDailyPriceService {

    private final StockRepository stockRepository;
    private final KisDailyPriceClient kisDailyPriceClient;
    private final StockDailyPriceWriter stockDailyPriceWriter;

    public StockDailyPriceSaveResult saveDailyPrices(
            String stockCode,
            LocalDate startDate,
            LocalDate endDate) {

        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "종목을 찾을 수 없습니다."));

        List<KisDailyPrice> dailyPrices = kisDailyPriceClient
                .getDailyPrices(stockCode, startDate, endDate);

        return stockDailyPriceWriter.write(stock, dailyPrices);
    }
}
