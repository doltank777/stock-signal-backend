package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalStockUniverseFingerprintTest {

    private final OperationalStockUniverseFingerprint fingerprint =
            new OperationalStockUniverseFingerprint();

    @Test
    void isOrderIndependentButChangesWhenMembershipChanges() {
        String original = fingerprint.calculate(
                stocks("000001", "000002", "000003"));
        String reordered = fingerprint.calculate(
                stocks("000003", "000001", "000002"));
        String sameCountChanged = fingerprint.calculate(
                stocks("000001", "000002", "000004"));
        String newListing = fingerprint.calculate(
                stocks("000001", "000002", "000003", "000004"));

        assertThat(reordered).isEqualTo(original);
        assertThat(sameCountChanged).isNotEqualTo(original);
        assertThat(newListing).isNotEqualTo(original);
        assertThat(original).hasSize(64);
    }

    private List<Stock> stocks(String... codes) {
        return java.util.Arrays.stream(codes)
                .map(code -> Stock.builder().stockCode(code)
                        .stockName(code).marketType(MarketType.KOSPI).build())
                .toList();
    }
}
