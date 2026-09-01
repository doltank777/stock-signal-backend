package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.InstrumentType;
import com.stockapp.domain.stock.MarketType;

import java.time.LocalDate;

public record KisMasterReconciliationCandidate(
        String stockCode,
        String stockName,
        MarketType market,
        InstrumentType instrumentType,
        LocalDate listingDate,
        boolean suspended,
        boolean liquidationTrading
) {
}
