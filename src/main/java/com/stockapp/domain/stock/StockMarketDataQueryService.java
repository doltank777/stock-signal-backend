package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockMarketDataQueryService {

    private final StockPriceRepository stockPriceRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;

    public Optional<LatestStockSnapshot> findLatestSnapshotForDate(
            Stock stock,
            LocalDate baseDate
    ) {
        validateStock(stock);
        validateBaseDate(baseDate);

        return stockPriceRepository
                .findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
                        stock.getStockCode(), baseDate)
                .map(this::toLatestStockSnapshot);
    }

    public List<DailyPriceData> findRecentDailyPricesBefore(
            Stock stock,
            LocalDate baseDate,
            int period
    ) {
        validateStock(stock);
        validateBaseDate(baseDate);
        validatePeriod(period);

        List<StockDailyPrice> prices = new ArrayList<>(
                stockDailyPriceRepository
                        .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                                stock,
                                baseDate,
                                PageRequest.of(0, period)));
        Collections.reverse(prices);

        return prices.stream()
                .map(this::toDailyPriceData)
                .toList();
    }

    private LatestStockSnapshot toLatestStockSnapshot(StockPrice price) {
        return new LatestStockSnapshot(
                price.getStockCode(),
                price.getTradeDate(),
                price.getCurrentPrice(),
                price.getChangeRate(),
                price.getVolume(),
                price.getCollectedAt());
    }

    private DailyPriceData toDailyPriceData(StockDailyPrice price) {
        return new DailyPriceData(
                price.getTradeDate(),
                price.getClosePrice(),
                price.getVolume());
    }

    private void validateStock(Stock stock) {
        if (stock == null) {
            throw new IllegalArgumentException("종목은 필수입니다.");
        }
    }

    private void validateBaseDate(LocalDate baseDate) {
        if (baseDate == null) {
            throw new IllegalArgumentException("조회 기준일은 필수입니다.");
        }
    }

    private void validatePeriod(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("조회 기간은 1 이상이어야 합니다.");
        }
    }
}
