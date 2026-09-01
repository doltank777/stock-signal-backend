package com.stockapp.domain.stock;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "stocks", indexes = {
        @Index(name = "idx_stocks_standard_code", columnList = "standard_code"),
        @Index(name = "idx_stocks_master_presence",
                columnList = "present_in_latest_master, market_type"),
        @Index(name = "idx_stocks_master_sync_execution",
                columnList = "master_sync_execution_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, unique = true, length = 20)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 20)
    private MarketType marketType;

    @Column(name = "standard_code", length = 12)
    private String standardCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", length = 40)
    private InstrumentType instrumentType;

    @Column(name = "security_group_code", length = 10)
    private String securityGroupCode;

    @Column(name = "preferred_stock_code", length = 10)
    private String preferredStockCode;

    @Column(name = "etp_product_code", length = 10)
    private String etpProductCode;

    @Column(name = "listing_date")
    private LocalDate listingDate;

    @Column
    private Boolean spac;

    @Column
    private Boolean suspended;

    @Column(name = "liquidation_trading")
    private Boolean liquidationTrading;

    @Column(name = "managed_issue")
    private Boolean managedIssue;

    @Column(name = "present_in_latest_master")
    private Boolean presentInLatestMaster;

    @Column(name = "master_observed_at")
    private Instant masterObservedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "master_sync_execution_id",
            foreignKey = @ForeignKey(name = "fk_stocks_master_sync_execution"))
    private KisMasterSyncExecution masterSyncExecution;

    public void updateStockInfo(
            String stockName,
            MarketType marketType
    ) {
        this.stockName = stockName;
        this.marketType = marketType;
    }
}
