package com.stockapp.domain.stock;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StockDailyPriceRepository
        extends JpaRepository<StockDailyPrice, Long> {

    List<StockDailyPrice> findByStockAndTradeDateBeforeOrderByTradeDateDesc(
            Stock stock,
            LocalDate baseDate,
            Pageable pageable
    );

    boolean existsByStockAndTradeDate(
            Stock stock,
            LocalDate tradeDate
    );
}
