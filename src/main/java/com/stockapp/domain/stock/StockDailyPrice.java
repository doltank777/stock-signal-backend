package com.stockapp.domain.stock;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "stock_daily_prices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_daily_prices_stock_trade_date",
                columnNames = {"stock_id", "trade_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockDailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false)
    private Long openPrice;

    @Column(name = "high_price", nullable = false)
    private Long highPrice;

    @Column(name = "low_price", nullable = false)
    private Long lowPrice;

    @Column(name = "close_price", nullable = false)
    private Long closePrice;

    @Column(nullable = false)
    private Long volume;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @PrePersist
    public void prePersist() {
        this.collectedAt = LocalDateTime.now();
    }

    public boolean updateFinalizedValues(
            Long openPrice,
            Long highPrice,
            Long lowPrice,
            Long closePrice,
            Long volume
    ) {
        if (Objects.equals(this.openPrice, openPrice)
                && Objects.equals(this.highPrice, highPrice)
                && Objects.equals(this.lowPrice, lowPrice)
                && Objects.equals(this.closePrice, closePrice)
                && Objects.equals(this.volume, volume)) {
            return false;
        }
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        return true;
    }
}
