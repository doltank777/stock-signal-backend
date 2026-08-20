package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface KrxTradingDayRepository
        extends JpaRepository<KrxTradingDay, LocalDate> {

    Optional<KrxTradingDay>
    findFirstByTradeDateBeforeAndTradingDayTrueOrderByTradeDateDesc(
            LocalDate tradeDate);

    long countByTradeDateGreaterThanEqualAndTradeDateLessThan(
            LocalDate startDate, LocalDate endDate);
}
