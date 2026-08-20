package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceFinalizationExecutionRepository
        extends JpaRepository<DailyPriceFinalizationExecution, Long> {

    Optional<DailyPriceFinalizationExecution> findByTargetTradeDate(
            LocalDate targetTradeDate);

    List<DailyPriceFinalizationExecution> findByStatusOrderByStartedAtAsc(
            DailyPriceFinalizationExecutionStatus status);

    List<DailyPriceFinalizationExecution> findByReadyFalseOrderByStartedAtAsc();

    Optional<DailyPriceFinalizationExecution> findFirstByOrderByStartedAtDesc();
}
