package com.stockapp.domain.signal;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.stock.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SignalRepository extends JpaRepository<Signal, Long> {

    boolean existsByStockAndSearchConditionAndDetectedAtAfter(
            Stock stock,
            SearchCondition searchCondition,
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
