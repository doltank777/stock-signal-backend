package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;

import java.time.LocalDate;
import java.util.Objects;

public record KisMasterNormalizedRecord(
        MarketType market,
        String stockCode,
        String standardCode,
        String stockName,
        LocalDate listingDate,
        InstrumentType instrumentType,
        boolean instrumentSupported,
        String securityGroupCode,
        String preferredStockCode,
        String etpProductCode,
        boolean spac,
        boolean suspended,
        boolean liquidationTrading,
        boolean managedIssue,
        KisMasterRawRecord rawRecord
) {
    public KisMasterNormalizedRecord {
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(listingDate, "listingDate");
        Objects.requireNonNull(instrumentType, "instrumentType");
        Objects.requireNonNull(rawRecord, "rawRecord");
    }

    public boolean currentEligible() {
        return instrumentSupported && !suspended && !liquidationTrading;
    }
}
