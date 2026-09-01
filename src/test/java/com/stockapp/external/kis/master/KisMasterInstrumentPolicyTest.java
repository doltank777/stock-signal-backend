package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.InstrumentType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class KisMasterInstrumentPolicyTest {

    @Test
    void defaultPolicyMatchesConfirmedUniverseContract() {
        KisMasterInstrumentPolicy policy = new KisMasterInstrumentPolicy();
        EnumSet<InstrumentType> supported = EnumSet.of(
                InstrumentType.COMMON_STOCK,
                InstrumentType.FOREIGN_STOCK,
                InstrumentType.DEPOSITARY_RECEIPT,
                InstrumentType.REIT,
                InstrumentType.INFRASTRUCTURE_FUND,
                InstrumentType.LISTED_FUND,
                InstrumentType.SPAC);

        for (InstrumentType type : InstrumentType.values()) {
            assertThat(policy.supports(type))
                    .as("default support for %s", type)
                    .isEqualTo(supported.contains(type));
        }
    }

    @Test
    void spacAndPreferredPoliciesCanChangeWithoutApplicationProperties() {
        KisMasterInstrumentPolicy policy = new KisMasterInstrumentPolicy(false, true);

        assertThat(policy.supports(InstrumentType.SPAC)).isFalse();
        assertThat(policy.supports(InstrumentType.PREFERRED_STOCK)).isTrue();
    }
}
