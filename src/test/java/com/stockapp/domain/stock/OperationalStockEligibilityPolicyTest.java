package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalStockEligibilityPolicyTest {

    private final OperationalStockEligibilityPolicy policy =
            new OperationalStockEligibilityPolicy(
                    new SupportedInstrumentPolicy());

    @Test
    void historyEligibilityUsesMarketPresenceAndSupportedInstrument() {
        assertHistory(true, stock(MarketType.KOSPI, true,
                InstrumentType.COMMON_STOCK, true, true));
        assertHistory(true, stock(MarketType.KOSDAQ, true,
                InstrumentType.SPAC, null, null));
        assertHistory(false, stock(MarketType.KONEX, true,
                InstrumentType.COMMON_STOCK, false, false));
        assertHistory(false, stock(MarketType.KOSPI, false,
                InstrumentType.COMMON_STOCK, false, false));
        assertHistory(false, stock(MarketType.KOSPI, null,
                InstrumentType.COMMON_STOCK, false, false));
        assertHistory(false, stock(MarketType.KOSPI, true,
                InstrumentType.PREFERRED_STOCK, false, false));
        assertHistory(false, stock(MarketType.KOSPI, true,
                null, false, false));
        assertThat(policy.isHistoryEligible(null)).isFalse();
    }

    @Test
    void currentEligibilityFailsClosedForStatusValues() {
        assertCurrent(true, stock(MarketType.KOSPI, true,
                InstrumentType.COMMON_STOCK, false, false));
        assertCurrent(false, stock(MarketType.KOSPI, true,
                InstrumentType.COMMON_STOCK, true, false));
        assertCurrent(false, stock(MarketType.KOSPI, true,
                InstrumentType.COMMON_STOCK, null, false));
        assertCurrent(false, stock(MarketType.KOSPI, true,
                InstrumentType.COMMON_STOCK, false, true));
        assertCurrent(false, stock(MarketType.KOSPI, true,
                InstrumentType.COMMON_STOCK, false, null));
        assertCurrent(false, stock(MarketType.KONEX, true,
                InstrumentType.COMMON_STOCK, false, false));
        assertThat(policy.isCurrentEligible(null)).isFalse();
    }

    private void assertHistory(boolean expected, Stock stock) {
        assertThat(policy.isHistoryEligible(stock)).isEqualTo(expected);
    }

    private void assertCurrent(boolean expected, Stock stock) {
        assertThat(policy.isCurrentEligible(stock)).isEqualTo(expected);
    }

    private Stock stock(
            MarketType market,
            Boolean present,
            InstrumentType instrumentType,
            Boolean suspended,
            Boolean liquidationTrading
    ) {
        return Stock.builder()
                .stockCode("000001")
                .stockName("Stock")
                .marketType(market)
                .presentInLatestMaster(present)
                .instrumentType(instrumentType)
                .suspended(suspended)
                .liquidationTrading(liquidationTrading)
                .build();
    }
}
