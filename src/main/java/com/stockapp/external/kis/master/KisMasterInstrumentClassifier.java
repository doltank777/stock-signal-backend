package com.stockapp.external.kis.master;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class KisMasterInstrumentClassifier {

    private static final Set<String> KNOWN_PREFERRED_CODES = Set.of("1", "2", "9");

    public InstrumentType classify(KisMasterRawRecord record) {
        String securityGroupCode = record.securityGroupCode();
        if (securityGroupCode == null) {
            return InstrumentType.OTHER;
        }
        return switch (securityGroupCode) {
            case "ST" -> classifyStock(record);
            case "FS" -> InstrumentType.FOREIGN_STOCK;
            case "DR" -> InstrumentType.DEPOSITARY_RECEIPT;
            case "RT" -> InstrumentType.REIT;
            case "IF" -> InstrumentType.INFRASTRUCTURE_FUND;
            case "MF" -> InstrumentType.LISTED_FUND;
            case "EF" -> InstrumentType.ETF;
            case "EN" -> InstrumentType.ETN;
            case "BC" -> InstrumentType.BENEFICIARY_CERTIFICATE;
            case "PF" -> InstrumentType.FUND_PRODUCT;
            case "SR" -> InstrumentType.SUBSCRIPTION_RIGHT;
            case "SW" -> InstrumentType.WARRANT;
            default -> InstrumentType.OTHER;
        };
    }

    private InstrumentType classifyStock(KisMasterRawRecord record) {
        if (KNOWN_PREFERRED_CODES.contains(record.preferredStockCode())) {
            return InstrumentType.PREFERRED_STOCK;
        }
        if (!"0".equals(record.preferredStockCode())) {
            return InstrumentType.OTHER;
        }
        return record.spac() ? InstrumentType.SPAC : InstrumentType.COMMON_STOCK;
    }
}
