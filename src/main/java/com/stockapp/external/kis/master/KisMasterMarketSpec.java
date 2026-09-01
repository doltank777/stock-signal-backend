package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;

import java.net.URI;

enum KisMasterMarketSpec {
    KOSPI(
            MarketType.KOSPI,
            URI.create("https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip"),
            "kospi_code.mst"),
    KOSDAQ(
            MarketType.KOSDAQ,
            URI.create("https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip"),
            "kosdaq_code.mst");

    private final MarketType market;
    private final URI downloadUri;
    private final String entryName;

    KisMasterMarketSpec(MarketType market, URI downloadUri, String entryName) {
        this.market = market;
        this.downloadUri = downloadUri;
        this.entryName = entryName;
    }

    MarketType market() {
        return market;
    }

    URI downloadUri() {
        return downloadUri;
    }

    String entryName() {
        return entryName;
    }

    static KisMasterMarketSpec from(MarketType market) {
        if (market == null) {
            throw new IllegalArgumentException("KIS Master market must not be null");
        }
        for (KisMasterMarketSpec spec : values()) {
            if (spec.market == market) {
                return spec;
            }
        }
        throw new IllegalArgumentException("Unsupported KIS Master market: " + market);
    }
}
