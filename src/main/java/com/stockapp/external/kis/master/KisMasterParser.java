package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;

public interface KisMasterParser {

    MarketType market();

    KisMasterParseResult parse(byte[] masterContent);
}
