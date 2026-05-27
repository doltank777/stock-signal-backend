package com.stockapp.domain.signal;

import com.stockapp.domain.stock.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SignalRepository extends JpaRepository<Signal, Long> {

    // ✅ 최근 시그널 1개 조회 (중복 방지용)
    Optional<Signal> findTopByStockAndSignalTypeOrderByDetectedAtDesc(
            Stock stock,
            SignalType signalType
    );

    // ✅ 일정 시간 이후 중복 방지
    boolean existsByStockAndSignalTypeAndDetectedAtAfter(
            Stock stock,
            SignalType signalType,
            LocalDateTime detectedAt
    );

    // ✅ 추천 리스트 조회 - Stock까지 함께 조회해서 LazyInitializationException 방지
    @Query("""
            SELECT s
            FROM Signal s
            JOIN FETCH s.stock
            ORDER BY s.detectedAt DESC
            """)
    List<Signal> findAllWithStockOrderByDetectedAtDesc();
}