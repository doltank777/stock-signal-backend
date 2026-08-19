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

    // ✅ 추천 리스트 조회 - Stock과 SearchCondition을 함께 조회해서 지연 로딩 방지
    @Query("""
            SELECT s
            FROM Signal s
            JOIN FETCH s.stock
            LEFT JOIN FETCH s.searchCondition
            ORDER BY s.detectedAt DESC
            """)
    List<Signal> findAllWithStockOrderByDetectedAtDesc();
}
