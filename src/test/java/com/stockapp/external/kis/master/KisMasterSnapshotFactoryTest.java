package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KisMasterSnapshotFactoryTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T01:23:45Z");

    private final KisMasterSnapshotFactory factory = new KisMasterSnapshotFactory(
            new KisMasterInstrumentClassifier(),
            new KisMasterInstrumentPolicy(),
            new KisMasterSnapshotValidator(),
            Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));

    @Test
    void normalizesIdentityClassificationStatusAndObservedTime() {
        KisMasterRawRecord raw = raw(
                MarketType.KOSPI, "005930", "KR7005930003", "삼성전자",
                "ST", "0", false, false, false, true);

        KisMasterSnapshot snapshot = factory.create(
                MarketType.KOSPI, parsed(raw));

        assertThat(snapshot.market()).isEqualTo(MarketType.KOSPI);
        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
        assertThat(snapshot.rawParsedRowCount()).isEqualTo(1);
        assertThat(snapshot.normalizedRowCount()).isEqualTo(1);
        assertThat(snapshot.supportedInstrumentCount()).isEqualTo(1);
        assertThat(snapshot.unsupportedInstrumentCount()).isZero();
        assertThat(snapshot.publishable()).isTrue();
        assertThat(snapshot.records()).singleElement().satisfies(record -> {
            assertThat(record.stockCode()).isEqualTo("005930");
            assertThat(record.standardCode()).isEqualTo("KR7005930003");
            assertThat(record.instrumentType()).isEqualTo(InstrumentType.COMMON_STOCK);
            assertThat(record.instrumentSupported()).isTrue();
            assertThat(record.managedIssue()).isTrue();
            assertThat(record.currentEligible()).isTrue();
            assertThat(record.rawRecord()).isSameAs(raw);
        });
    }

    @Test
    void currentEligibilitySeparatesInstrumentPolicyFromTradingStatus() {
        KisMasterSnapshot snapshot = factory.create(
                MarketType.KOSPI,
                parsed(
                        raw(MarketType.KOSPI, "000001", "KR7000000001", "정상주",
                                "ST", "0", false, false, false, false),
                        raw(MarketType.KOSPI, "000002", "KR7000000002", "정지주",
                                "ST", "0", false, true, false, false),
                        raw(MarketType.KOSPI, "000003", "KR7000000003", "정리주",
                                "ST", "0", false, false, true, false),
                        raw(MarketType.KOSPI, "000004", "KR7000000004", "ETF",
                                "EF", "0", false, false, false, false),
                        raw(MarketType.KOSPI, "000005", "KR7000000005", "스팩",
                                "ST", "0", true, false, false, false),
                        raw(MarketType.KOSPI, "000006", "KR7000000006", "우선주",
                                "ST", "1", false, false, false, false)));

        assertThat(snapshot.records()).extracting(KisMasterNormalizedRecord::currentEligible)
                .containsExactly(true, false, false, false, true, false);
        assertThat(snapshot.records().get(0).managedIssue()).isFalse();
    }

    static KisMasterParseResult parsed(KisMasterRawRecord... records) {
        return new KisMasterParseResult(
                List.of(records),
                new KisMasterParseSummary(records.length, 0, 0, 0));
    }

    static KisMasterRawRecord raw(
            MarketType market,
            String code,
            String standardCode,
            String name,
            String group,
            String preferred,
            boolean spac,
            boolean suspended,
            boolean liquidation,
            boolean managed
    ) {
        return new KisMasterRawRecord(
                market,
                code,
                standardCode,
                name,
                group,
                preferred,
                "",
                spac,
                suspended,
                liquidation,
                managed,
                LocalDate.of(2026, 1, 1),
                "",
                List.of());
    }
}
