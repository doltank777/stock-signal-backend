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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_condition_id")
    private SearchCondition searchCondition;

    // 신호 종류: 거래량 급증, 이동평균 돌파 등
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SignalType signalType;

    // 신호 설명
    @Column(nullable = false, length = 255)
    private String message;

    // 기준값 예: 평균 거래량
    private Long baseValue;

    // 현재값 예: 현재 거래량
    private Long currentValue;

    // 증가율 예: 235.5%
    private Double changeRate;

    // 신호 발생 시간
    @Column(nullable = false)
    private LocalDateTime detectedAt;

    private Signal(
            Stock stock,
            SearchCondition searchCondition,
            SignalType signalType,
            String message,
            Long baseValue,
            Long currentValue,
            Double changeRate,
            LocalDateTime detectedAt
    ) {
        this.stock = stock;
        this.searchCondition = searchCondition;
        this.signalType = signalType;
        this.message = message;
        this.baseValue = baseValue;
        this.currentValue = currentValue;
        this.changeRate = changeRate;
        this.detectedAt = detectedAt;
    }

    public static Signal createVolumeSpike(
            Stock stock,
            Long averageVolume,
            Long currentVolume,
            Double changeRate
    ) {
        return new Signal(
                stock,
                null,
                SignalType.VOLUME_SPIKE,
                "거래량 급증 신호 발생",
                averageVolume,
                currentVolume,
                changeRate,
                LocalDateTime.now()
        );
    }

    public static Signal createMovingAverageBreakout(
            Stock stock,
            Long averagePrice,
            Long currentPrice,
            Double changeRate
    ) {
        return new Signal(
                stock,
                null,
                SignalType.MOVING_AVERAGE_BREAKOUT,
                "이동평균 돌파 신호 발생",
                averagePrice,
                currentPrice,
                changeRate,
                LocalDateTime.now()
        );
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
                SignalType.SEARCH_CONDITION_MATCH,
                "검색식 SIGNAL 조건 일치",
                null,
                null,
                null,
                detectedAt
        );
    }
}
