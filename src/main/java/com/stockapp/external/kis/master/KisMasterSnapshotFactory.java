package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class KisMasterSnapshotFactory {

    private final KisMasterInstrumentClassifier classifier;
    private final KisMasterInstrumentPolicy policy;
    private final KisMasterSnapshotValidator validator;
    private final Clock clock;

    public KisMasterSnapshotFactory(
            KisMasterInstrumentClassifier classifier,
            KisMasterInstrumentPolicy policy,
            KisMasterSnapshotValidator validator,
            Clock clock
    ) {
        this.classifier = classifier;
        this.policy = policy;
        this.validator = validator;
        this.clock = clock;
    }

    public KisMasterSnapshot create(MarketType market, KisMasterParseResult rawResult) {
        List<KisMasterNormalizedRecord> records = new ArrayList<>(rawResult.records().size());
        for (KisMasterRawRecord raw : rawResult.records()) {
            InstrumentType type = classifier.classify(raw);
            records.add(normalize(raw, type, policy.supports(type)));
        }
        KisMasterSnapshotValidationResult validation =
                validator.validate(market, rawResult, records);
        return new KisMasterSnapshot(
                market,
                Instant.now(clock),
                records,
                rawResult.summary().parsedRowCount(),
                records.size(),
                validation.supportedInstrumentCount(),
                validation.unsupportedInstrumentCount(),
                validation);
    }

    private KisMasterNormalizedRecord normalize(
            KisMasterRawRecord raw,
            InstrumentType type,
            boolean supported
    ) {
        return new KisMasterNormalizedRecord(
                raw.market(),
                raw.shortCode(),
                raw.standardCode(),
                raw.stockName(),
                raw.listingDate(),
                type,
                supported,
                raw.securityGroupCode(),
                raw.preferredStockCode(),
                raw.etpProductCode(),
                raw.spac(),
                raw.suspended(),
                raw.liquidationTrading(),
                raw.managedIssue(),
                raw);
    }
}
