package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByStockCode(String stockCode);

    boolean existsByStockCode(String stockCode);

    List<Stock> findByStockNameContainingOrStockCodeContaining(
            String stockName,
            String stockCode
    );

}