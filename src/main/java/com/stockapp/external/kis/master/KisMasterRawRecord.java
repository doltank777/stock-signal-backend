package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;

import java.time.LocalDate;
import java.util.List;

public record KisMasterRawRecord(
        MarketType market,
        String shortCode,
        String standardCode,
        String stockName,
        String securityGroupCode,
        String preferredStockCode,
        String etpProductCode,
        boolean spac,
        boolean suspended,
        boolean liquidationTrading,
        boolean managedIssue,
        LocalDate listingDate,
        String rawSuffix,
        List<String> rawSuffixFields
) {
    public KisMasterRawRecord {
        rawSuffixFields = List.copyOf(rawSuffixFields);
    }
}
