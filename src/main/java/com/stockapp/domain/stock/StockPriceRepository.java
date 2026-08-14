package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    Optional<StockPrice> findTopByStockCodeOrderByCollectedAtDesc(String stockCode);

    Optional<StockPrice> findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
            String stockCode,
            LocalDate tradeDate
    );

    List<StockPrice> findTop5ByStockCodeOrderByCollectedAtDesc(String stockCode);

    List<StockPrice> findTop6ByStockCodeOrderByCollectedAtDesc(String stockCode);
}
