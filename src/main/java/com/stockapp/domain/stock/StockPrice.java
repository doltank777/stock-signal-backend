package com.stockapp.domain.stock;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(
        name = "stock_prices",
        indexes = @Index(
                name = "idx_stock_prices_stock_trade_collected_id",
                columnList = "stock_code, trade_date, collected_at, id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockPrice {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

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
        if (this.collectedAt == null) {
            this.collectedAt = LocalDateTime.now(KOREA_ZONE);
        }
    }
}
