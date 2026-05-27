package com.stockapp.domain.signal;

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
            SignalType signalType,
            String message,
            Long baseValue,
            Long currentValue,
            Double changeRate
    ) {
        this.stock = stock;
        this.signalType = signalType;
        this.message = message;
        this.baseValue = baseValue;
        this.currentValue = currentValue;
        this.changeRate = changeRate;
        this.detectedAt = LocalDateTime.now();
    }

    public static Signal createVolumeSpike(
            Stock stock,
            Long averageVolume,
            Long currentVolume,
            Double changeRate
    ) {
        return new Signal(
                stock,
                SignalType.VOLUME_SPIKE,
                "거래량 급증 신호 발생",
                averageVolume,
                currentVolume,
                changeRate
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
                SignalType.MOVING_AVERAGE_BREAKOUT,
                "이동평균 돌파 신호 발생",
                averagePrice,
                currentPrice,
                changeRate
        );
    }
}