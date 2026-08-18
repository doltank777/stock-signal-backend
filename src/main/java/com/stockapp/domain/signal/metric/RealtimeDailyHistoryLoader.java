package com.stockapp.domain.signal.metric;

import com.stockapp.domain.stock.StockDailyPrice;
import com.stockapp.domain.stock.StockDailyPriceRepository;
import com.stockapp.domain.stock.dto.DailyPriceData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RealtimeDailyHistoryLoader {

    private final StockDailyPriceRepository stockDailyPriceRepository;

    @Transactional(readOnly = true)
    public List<DailyPriceData> load(
            Long stockId,
            LocalDate tradeDate,
            int period
    ) {
        if (stockId == null) {
            throw new IllegalArgumentException("stockId is required");
        }
        if (tradeDate == null) {
            throw new IllegalArgumentException("tradeDate is required");
        }
        if (period < 1) {
            throw new IllegalArgumentException("period must be positive");
        }

        List<StockDailyPrice> prices = new ArrayList<>(
                stockDailyPriceRepository
                        .findByStockIdAndTradeDateBeforeOrderByTradeDateDesc(
                                stockId,
                                tradeDate,
                                PageRequest.of(0, period)));
        Collections.reverse(prices);
        return prices.stream()
                .map(price -> new DailyPriceData(
                        price.getTradeDate(),
                        price.getClosePrice(),
                        price.getVolume()))
                .toList();
    }
}
