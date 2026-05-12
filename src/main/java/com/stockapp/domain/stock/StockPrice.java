package com.stockapp.domain.stock;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "current_price", nullable = false)
    private Long currentPrice;

    @Column(name = "change_rate", nullable = false)
    private Double changeRate;

    @Column(nullable = false)
    private Long volume;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @PrePersist
    public void prePersist() {
        this.collectedAt = LocalDateTime.now();
    }
}