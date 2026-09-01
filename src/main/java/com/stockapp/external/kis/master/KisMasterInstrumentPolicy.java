package com.stockapp.external.kis.master;

import org.springframework.stereotype.Component;

@Component
public class KisMasterInstrumentPolicy {

    private final boolean includeSpac;
    private final boolean includePreferredStock;

    public KisMasterInstrumentPolicy() {
        this(true, false);
    }

    public KisMasterInstrumentPolicy(boolean includeSpac, boolean includePreferredStock) {
        this.includeSpac = includeSpac;
        this.includePreferredStock = includePreferredStock;
    }

    public boolean supports(InstrumentType instrumentType) {
        return switch (instrumentType) {
            case COMMON_STOCK,
                    FOREIGN_STOCK,
                    DEPOSITARY_RECEIPT,
                    REIT,
                    INFRASTRUCTURE_FUND,
                    LISTED_FUND -> true;
            case SPAC -> includeSpac;
            case PREFERRED_STOCK -> includePreferredStock;
            case ETF,
                    ETN,
                    BENEFICIARY_CERTIFICATE,
                    FUND_PRODUCT,
                    SUBSCRIPTION_RIGHT,
                    WARRANT,
                    OTHER -> false;
        };
    }
}
