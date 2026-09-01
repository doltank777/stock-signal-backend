package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;

public record KisMasterMissingStock(
        String stockCode,
        String stockName,
        MarketType market,
        Boolean presentInLatestMaster
) {
}
