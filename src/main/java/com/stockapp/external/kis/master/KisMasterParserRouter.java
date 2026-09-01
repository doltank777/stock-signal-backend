package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class KisMasterParserRouter {

    private final Map<MarketType, KisMasterParser> parsers;

    public KisMasterParserRouter(List<KisMasterParser> parsers) {
        EnumMap<MarketType, KisMasterParser> byMarket = new EnumMap<>(MarketType.class);
        for (KisMasterParser parser : parsers) {
            KisMasterParser previous = byMarket.put(parser.market(), parser);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate KIS Master parser for market: " + parser.market());
            }
        }
        this.parsers = Map.copyOf(byMarket);
    }

    public KisMasterParseResult parse(MarketType market, byte[] content) {
        KisMasterMarketSpec.from(market);
        KisMasterParser parser = parsers.get(market);
        if (parser == null) {
            throw new IllegalStateException("KIS Master parser is not configured: " + market);
        }
        return parser.parse(content);
    }
}
