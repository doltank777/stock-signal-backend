package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.InstrumentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class KisMasterSnapshotValidator {

    private static final Set<String> KNOWN_SECURITY_GROUPS = Set.of(
            "ST", "EF", "EN", "RT", "IF", "MF",
            "FS", "DR", "BC", "PF", "SR", "SW");

    public KisMasterSnapshotValidationResult validate(
            MarketType expectedMarket,
            KisMasterParseResult rawResult,
            List<KisMasterNormalizedRecord> records
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (records.isEmpty()) {
            errors.add("normalized Master records must not be empty");
        }
        if (rawResult.summary().parsedRowCount() != rawResult.records().size()) {
            errors.add("raw parsed row count does not match raw records size");
        }
        if (rawResult.summary().parsedRowCount() != records.size()) {
            errors.add("parsed row count does not match normalized row count");
        }
        if (rawResult.summary().blankCodeCount() > 0) {
            errors.add("raw Master contains blank short codes");
        }
        if (rawResult.summary().invalidRowCount() > 0) {
            errors.add("raw Master contains invalid rows");
        }

        Map<String, Integer> shortCodeCounts = new HashMap<>();
        Map<String, Integer> standardCodeCounts = new HashMap<>();
        Set<String> unknownGroups = new LinkedHashSet<>();
        int supported = 0;
        int unknownInstruments = 0;
        for (KisMasterNormalizedRecord record : records) {
            if (record.market() != expectedMarket) {
                errors.add("record market does not match snapshot market: " + record.stockCode());
            }
            validateIdentity(record, errors);
            validateRawDomain(record, errors);
            shortCodeCounts.merge(record.stockCode(), 1, Integer::sum);
            standardCodeCounts.merge(record.standardCode(), 1, Integer::sum);
            if (record.instrumentSupported()) {
                supported++;
            }
            if (record.instrumentType() == InstrumentType.OTHER) {
                unknownInstruments++;
            }
            if (!KNOWN_SECURITY_GROUPS.contains(record.securityGroupCode())) {
                unknownGroups.add(record.securityGroupCode());
            }
        }

        int duplicateShortCodes = duplicateCount(shortCodeCounts);
        duplicateShortCodes = Math.max(
                duplicateShortCodes, rawResult.summary().duplicateShortCodeCount());
        if (duplicateShortCodes > 0) {
            errors.add("duplicate short codes detected: " + duplicateShortCodes);
        }
        int duplicateStandardCodes = duplicateCount(standardCodeCounts);
        if (duplicateStandardCodes > 0) {
            warnings.add("duplicate standard codes detected: " + duplicateStandardCodes);
        }
        if (!unknownGroups.isEmpty()) {
            warnings.add("unknown security group codes detected: " + unknownGroups);
        }
        if (unknownInstruments > 0) {
            warnings.add("OTHER instruments detected: " + unknownInstruments);
        }

        return new KisMasterSnapshotValidationResult(
                errors.isEmpty()
                        ? KisMasterSnapshotValidationStatus.READY
                        : KisMasterSnapshotValidationStatus.NOT_READY,
                errors,
                warnings,
                rawResult.summary().parsedRowCount(),
                records.size(),
                supported,
                records.size() - supported,
                unknownInstruments,
                duplicateShortCodes,
                duplicateStandardCodes,
                unknownGroups);
    }

    private void validateIdentity(
            KisMasterNormalizedRecord record,
            List<String> errors
    ) {
        if (isBlank(record.stockCode())) {
            errors.add("stockCode must not be blank");
        }
        if (isBlank(record.standardCode())) {
            errors.add("standardCode must not be blank: " + record.stockCode());
        }
        if (isBlank(record.stockName())) {
            errors.add("stockName must not be blank: " + record.stockCode());
        }
        if (isBlank(record.securityGroupCode())) {
            errors.add("securityGroupCode must not be blank: " + record.stockCode());
        }
        if (isBlank(record.preferredStockCode())) {
            errors.add("preferredStockCode must not be blank: " + record.stockCode());
        }
    }

    private void validateRawDomain(
            KisMasterNormalizedRecord record,
            List<String> errors
    ) {
        if (record.spac()
                && (!("ST".equals(record.securityGroupCode()))
                || !"0".equals(record.preferredStockCode()))) {
            errors.add("SPAC raw fields are inconsistent: " + record.stockCode());
        }
    }

    private int duplicateCount(Map<String, Integer> counts) {
        return counts.values().stream()
                .mapToInt(count -> Math.max(0, count - 1))
                .sum();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
