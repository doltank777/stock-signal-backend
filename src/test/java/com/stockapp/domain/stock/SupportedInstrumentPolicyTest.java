package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedInstrumentPolicyTest {

    private final SupportedInstrumentPolicy policy =
            new SupportedInstrumentPolicy();

    @Test
    void supportsExactlyTheOperationalInstrumentTypes() {
        Set<InstrumentType> expected = Set.of(
                InstrumentType.COMMON_STOCK,
                InstrumentType.SPAC,
                InstrumentType.FOREIGN_STOCK,
                InstrumentType.DEPOSITARY_RECEIPT,
                InstrumentType.REIT,
                InstrumentType.INFRASTRUCTURE_FUND,
                InstrumentType.LISTED_FUND);

        for (InstrumentType type : InstrumentType.values()) {
            assertThat(policy.isSupported(type))
                    .as("support for %s", type)
                    .isEqualTo(expected.contains(type));
        }
        assertThat(policy.isSupported(null)).isFalse();
        assertThat(policy.supportedTypes()).containsExactlyInAnyOrderElementsOf(
                expected);
    }
}
