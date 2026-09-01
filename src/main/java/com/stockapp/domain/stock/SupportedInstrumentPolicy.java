package com.stockapp.domain.stock;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SupportedInstrumentPolicy {

    private static final Set<InstrumentType> SUPPORTED_TYPES = Set.of(
            InstrumentType.COMMON_STOCK,
            InstrumentType.SPAC,
            InstrumentType.FOREIGN_STOCK,
            InstrumentType.DEPOSITARY_RECEIPT,
            InstrumentType.REIT,
            InstrumentType.INFRASTRUCTURE_FUND,
            InstrumentType.LISTED_FUND);

    public boolean isSupported(InstrumentType type) {
        return type != null && SUPPORTED_TYPES.contains(type);
    }

    public Set<InstrumentType> supportedTypes() {
        return SUPPORTED_TYPES;
    }
}
