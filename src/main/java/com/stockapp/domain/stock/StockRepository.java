package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByStockCode(String stockCode);

    Optional<Stock> findByStockCodeAndMarketTypeIn(
            String stockCode,
            List<MarketType> marketTypes
    );

    boolean existsByStockCode(String stockCode);

    List<Stock> findByMarketTypeInOrderByIdAsc(List<MarketType> marketTypes);

    List<Stock> findByStockNameContainingOrStockCodeContaining(
            String stockName,
            String stockCode
    );

}
