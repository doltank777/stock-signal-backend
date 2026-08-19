package com.stockapp.domain.signal;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "signals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Signal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 종목에서 발생한 신호인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_condition_id", nullable = false)
    private SearchCondition searchCondition;

    // 신호 설명
    @Column(nullable = false, length = 255)
    private String message;

    // 신호 발생 시간
    @Column(nullable = false)
    private LocalDateTime detectedAt;

    private Signal(
            Stock stock,
            SearchCondition searchCondition,
            String message,
            LocalDateTime detectedAt
    ) {
        this.stock = stock;
        this.searchCondition = searchCondition;
        this.message = message;
        this.detectedAt = detectedAt;
    }

    public static Signal createSearchConditionMatch(
            Stock stock,
            SearchCondition searchCondition,
            LocalDateTime detectedAt
    ) {
        if (stock == null) {
            throw new IllegalArgumentException("stock is required");
        }
        if (searchCondition == null) {
            throw new IllegalArgumentException("searchCondition is required");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("detectedAt is required");
        }
        return new Signal(
                stock,
                searchCondition,
                "검색식 SIGNAL 조건 일치",
                detectedAt
        );
    }
}
