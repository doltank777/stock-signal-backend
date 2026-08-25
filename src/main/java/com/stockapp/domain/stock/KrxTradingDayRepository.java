package com.stockapp.domain.stock;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface KrxTradingDayRepository
        extends JpaRepository<KrxTradingDay, LocalDate> {

    Optional<KrxTradingDay>
    findFirstByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
            LocalDate tradeDate);

    List<KrxTradingDay>
    findByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
            LocalDate tradeDate, Pageable pageable);

    long countByTradeDateGreaterThanEqualAndTradeDateLessThan(
            LocalDate startDate, LocalDate endDate);

    List<KrxTradingDay>
    findByTradeDateBetweenAndTradingDayTrueOrderByTradeDateAsc(
            LocalDate startDate, LocalDate endDate);

    long countByTradeDateBetween(
            LocalDate startDate, LocalDate endDate);
}
