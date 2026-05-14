package com.stockapp.domain.signal;

import com.stockapp.domain.stock.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SignalRepository extends JpaRepository<Signal, Long> {

    Optional<Signal> findTopByStockAndSignalTypeOrderByDetectedAtDesc(
            Stock stock,
            SignalType signalType
    );

    boolean existsByStockAndSignalTypeAndDetectedAtAfter(
            Stock stock,
            SignalType signalType,
            LocalDateTime detectedAt
    );
}