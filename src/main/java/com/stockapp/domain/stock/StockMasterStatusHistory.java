package com.stockapp.domain.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "stock_master_status_histories", indexes = {
        @Index(name = "idx_stock_master_history_stock_observed",
                columnList = "stock_id, observed_at"),
        @Index(name = "idx_stock_master_history_sync_execution",
                columnList = "master_sync_execution_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMasterStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "stock_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_master_history_stock"))
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private StockMasterStatusEventType eventType;

    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "effective_at")
    private Instant effectiveAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "master_sync_execution_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_stock_master_history_sync_execution"))
    private KisMasterSyncExecution masterSyncExecution;

    public static StockMasterStatusHistory create(
            Stock stock,
            StockMasterStatusEventType eventType,
            String oldValue,
            String newValue,
            Instant observedAt,
            Instant effectiveAt,
            KisMasterSyncExecution masterSyncExecution
    ) {
        if (stock == null || eventType == null || observedAt == null
                || masterSyncExecution == null) {
            throw new IllegalArgumentException(
                    "stock, eventType, observedAt, and execution are required");
        }
        if (oldValue == null && newValue == null) {
            throw new IllegalArgumentException(
                    "at least one history value must be present");
        }
        StockMasterStatusHistory history = new StockMasterStatusHistory();
        history.stock = stock;
        history.eventType = eventType;
        history.oldValue = oldValue;
        history.newValue = newValue;
        history.observedAt = observedAt;
        history.effectiveAt = effectiveAt;
        history.masterSyncExecution = masterSyncExecution;
        return history;
    }
}
