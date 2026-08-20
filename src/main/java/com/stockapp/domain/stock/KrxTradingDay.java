package com.stockapp.domain.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "krx_trading_days")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KrxTradingDay {

    public static final String KIS_SOURCE = "KIS";

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "trading_day", nullable = false)
    private boolean tradingDay;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "synchronized_at", nullable = false)
    private Instant synchronizedAt;

    public static KrxTradingDay create(LocalDate tradeDate,
                                       boolean tradingDay,
                                       String source,
                                       Instant synchronizedAt) {
        KrxTradingDay day = new KrxTradingDay();
        day.tradeDate = Objects.requireNonNull(tradeDate,
                "tradeDate is required");
        day.updateFromSource(tradingDay, source, synchronizedAt);
        return day;
    }

    public boolean updateFromSource(boolean tradingDay, String source,
                                    Instant synchronizedAt) {
        requireText(source, "source is required");
        Objects.requireNonNull(synchronizedAt, "synchronizedAt is required");
        boolean changed = this.tradingDay != tradingDay
                || !Objects.equals(this.source, source);
        this.tradingDay = tradingDay;
        this.source = source;
        this.synchronizedAt = synchronizedAt;
        return changed;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
