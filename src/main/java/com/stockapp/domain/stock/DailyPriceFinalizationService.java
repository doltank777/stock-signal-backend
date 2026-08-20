package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationResult;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyPriceFinalizationService {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final KisDailyPriceClient kisDailyPriceClient;
    private final StockDailyPriceWriter stockDailyPriceWriter;

    public DailyPriceFinalizationResult finalizeStock(
            String stockCode,
            LocalDate targetTradeDate
    ) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        if (targetTradeDate == null) {
            throw new IllegalArgumentException("targetTradeDate is required");
        }

        String normalizedStockCode = stockCode.trim();
        Stock stock = stockRepository.findByStockCodeAndMarketTypeIn(
                        normalizedStockCode, TARGET_MARKETS)
                .orElseThrow(() -> new IllegalArgumentException(
                        "KOSPI/KOSDAQ 일봉 Finalization 대상 종목을 찾을 수 없습니다: "
                                + normalizedStockCode));

        return finalizeStock(stock, targetTradeDate);
    }

    DailyPriceFinalizationResult finalizeStock(
            Stock stock,
            LocalDate targetTradeDate
    ) {
        if (stock == null) {
            throw new IllegalArgumentException("stock is required");
        }
        if (targetTradeDate == null) {
            throw new IllegalArgumentException("targetTradeDate is required");
        }
        String stockCode = stock.getStockCode();
        KisDailyPrice targetPrice = kisDailyPriceClient.getDailyPrices(
                        stockCode, targetTradeDate, targetTradeDate)
                .stream()
                .filter(price -> targetTradeDate.equals(price.getTradeDate()))
                .findFirst()
                .orElse(null);
        if (targetPrice == null) {
            return result(stockCode, targetTradeDate,
                    DailyPriceFinalizationStatus.NO_DATA);
        }

        DailyPriceFinalizationStatus status =
                stockDailyPriceWriter.finalizePrice(stock, targetPrice);
        return result(stockCode, targetTradeDate, status);
    }

    private DailyPriceFinalizationResult result(
            String stockCode,
            LocalDate targetTradeDate,
            DailyPriceFinalizationStatus status
    ) {
        return new DailyPriceFinalizationResult(
                stockCode, targetTradeDate, status);
    }
}
