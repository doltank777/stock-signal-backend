package com.stockapp.domain.stock;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    long countByStockAndTradeDateLessThanEqual(Stock stock, LocalDate endDate);

    @Query("select max(price.tradeDate) from StockDailyPrice price where price.stock = :stock")
    Optional<LocalDate> findLatestTradeDateByStock(@Param("stock") Stock stock);

    @Query("""
            select price.tradeDate
            from StockDailyPrice price
            where price.stock = :stock
              and price.tradeDate between :startDate and :endDate
            """)
    List<LocalDate> findTradeDates(
            @Param("stock") Stock stock,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
