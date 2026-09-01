package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.InstrumentType;
import com.stockapp.domain.stock.SupportedInstrumentPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KisMasterInstrumentPolicy {

    private final boolean includeSpac;
    private final boolean includePreferredStock;
    private final SupportedInstrumentPolicy supportedInstrumentPolicy;

    public KisMasterInstrumentPolicy() {
        this(new SupportedInstrumentPolicy(), true, false);
    }

    public KisMasterInstrumentPolicy(boolean includeSpac, boolean includePreferredStock) {
        this(new SupportedInstrumentPolicy(), includeSpac, includePreferredStock);
    }

    @Autowired
    public KisMasterInstrumentPolicy(
            SupportedInstrumentPolicy supportedInstrumentPolicy
    ) {
        this(supportedInstrumentPolicy, true, false);
    }

    private KisMasterInstrumentPolicy(
            SupportedInstrumentPolicy supportedInstrumentPolicy,
            boolean includeSpac,
            boolean includePreferredStock
    ) {
        this.supportedInstrumentPolicy = supportedInstrumentPolicy;
        this.includeSpac = includeSpac;
        this.includePreferredStock = includePreferredStock;
    }

    public boolean supports(InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.SPAC) {
            return includeSpac;
        }
        if (instrumentType == InstrumentType.PREFERRED_STOCK) {
            return includePreferredStock;
        }
        return supportedInstrumentPolicy.isSupported(instrumentType);
    }
}
