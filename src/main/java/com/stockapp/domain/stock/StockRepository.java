package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query("""
            select stock
            from Stock stock
            where stock.marketType in :marketTypes
              and stock.presentInLatestMaster = true
              and stock.instrumentType in :supportedTypes
            order by stock.id asc
            """)
    List<Stock> findHistoryEligibleStocks(
            @Param("marketTypes") Collection<MarketType> marketTypes,
            @Param("supportedTypes") Collection<InstrumentType> supportedTypes
    );

    @Query("""
            select stock
            from Stock stock
            where stock.marketType in :marketTypes
              and stock.presentInLatestMaster = true
              and stock.instrumentType in :supportedTypes
              and stock.suspended = false
              and stock.liquidationTrading = false
            order by stock.id asc
            """)
    List<Stock> findCurrentEligibleStocks(
            @Param("marketTypes") Collection<MarketType> marketTypes,
            @Param("supportedTypes") Collection<InstrumentType> supportedTypes
    );

    List<Stock> findByStockCodeInAndMarketTypeIn(
            List<String> stockCodes,
            List<MarketType> marketTypes
    );

    List<Stock> findByStockNameContainingOrStockCodeContaining(
            String stockName,
            String stockCode
    );

}
