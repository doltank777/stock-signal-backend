package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    Optional<StockPrice> findTopByStockCodeOrderByCollectedAtDesc(String stockCode);
}