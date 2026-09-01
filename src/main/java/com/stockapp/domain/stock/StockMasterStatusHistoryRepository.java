package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMasterStatusHistoryRepository
        extends JpaRepository<StockMasterStatusHistory, Long> {

    List<StockMasterStatusHistory> findByStockIdOrderByObservedAtDescIdDesc(
            Long stockId);

    List<StockMasterStatusHistory>
    findByMasterSyncExecutionIdOrderByIdAsc(Long masterSyncExecutionId);
}
