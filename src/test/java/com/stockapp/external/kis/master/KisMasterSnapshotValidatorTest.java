package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.InstrumentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.stockapp.external.kis.master.KisMasterSnapshotFactoryTest.parsed;
import static com.stockapp.external.kis.master.KisMasterSnapshotFactoryTest.raw;
import static org.assertj.core.api.Assertions.assertThat;

class KisMasterSnapshotValidatorTest {

    private final KisMasterInstrumentClassifier classifier =
            new KisMasterInstrumentClassifier();
    private final KisMasterInstrumentPolicy policy = new KisMasterInstrumentPolicy();
    private final KisMasterSnapshotValidator validator = new KisMasterSnapshotValidator();

    @Test
    void acceptsNormalKospiAndKosdaqSnapshots() {
        assertThat(validate(MarketType.KOSPI, parsed(raw(
                MarketType.KOSPI, "005930", "KR7005930003", "삼성전자",
                "ST", "0", false, false, false, false))).ready()).isTrue();
        assertThat(validate(MarketType.KOSDAQ, parsed(raw(
                MarketType.KOSDAQ, "000250", "KR7000250001", "삼천당제약",
                "ST", "0", false, false, false, false))).ready()).isTrue();
    }

    @Test
    void duplicateShortCodeMakesSnapshotNotReady() {
        var first = raw(MarketType.KOSPI, "005930", "KR7005930003", "삼성전자",
                "ST", "0", false, false, false, false);
        var second = raw(MarketType.KOSPI, "005930", "KR7005930004", "중복",
                "ST", "0", false, false, false, false);
        KisMasterParseResult parsed = new KisMasterParseResult(
                List.of(first, second), new KisMasterParseSummary(2, 1, 0, 0));

        KisMasterSnapshotValidationResult result = validate(MarketType.KOSPI, parsed);

        assertThat(result.ready()).isFalse();
        assertThat(result.duplicateShortCodeCount()).isEqualTo(1);
        assertThat(result.errors()).anyMatch(error -> error.contains("duplicate short codes"));
    }

    @Test
    void duplicateStandardCodeIsReportedAsWarningWithoutAssumingUniquenessContract() {
        var first = raw(MarketType.KOSPI, "000001", "KR7000000001", "첫번째",
                "ST", "0", false, false, false, false);
        var second = raw(MarketType.KOSPI, "000002", "KR7000000001", "두번째",
                "ST", "0", false, false, false, false);

        KisMasterSnapshotValidationResult result = validate(
                MarketType.KOSPI, parsed(first, second));

        assertThat(result.ready()).isTrue();
        assertThat(result.duplicateStandardCodeCount()).isEqualTo(1);
        assertThat(result.warnings())
                .contains("duplicate standard codes detected: 1");
    }

    @Test
    void unknownSecurityGroupIsOtherUnsupportedButSnapshotRemainsReady() {
        KisMasterSnapshotValidationResult result = validate(
                MarketType.KOSPI,
                parsed(raw(MarketType.KOSPI, "000001", "KR7000000001", "신규상품",
                        "ZZ", "0", false, false, false, false)));

        assertThat(result.ready()).isTrue();
        assertThat(result.supportedInstrumentCount()).isZero();
        assertThat(result.unsupportedInstrumentCount()).isEqualTo(1);
        assertThat(result.unknownInstrumentCount()).isEqualTo(1);
        assertThat(result.unknownSecurityGroupCodes()).containsExactly("ZZ");
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void allUnsupportedInstrumentsCanStillBeACompleteSnapshot() {
        KisMasterSnapshotValidationResult result = validate(
                MarketType.KOSPI,
                parsed(
                        raw(MarketType.KOSPI, "000001", "KR7000000001", "ETF",
                                "EF", "0", false, false, false, false),
                        raw(MarketType.KOSPI, "000002", "KR7000000002", "ETN",
                                "EN", "0", false, false, false, false)));

        assertThat(result.ready()).isTrue();
        assertThat(result.supportedInstrumentCount()).isZero();
        assertThat(result.unsupportedInstrumentCount()).isEqualTo(2);
    }

    @Test
    void rejectsEmptyNormalizedResult() {
        KisMasterSnapshotValidationResult result = validator.validate(
                MarketType.KOSPI,
                new KisMasterParseResult(List.of(), new KisMasterParseSummary(0, 0, 0, 0)),
                List.of());

        assertThat(result.ready()).isFalse();
        assertThat(result.errors()).contains("normalized Master records must not be empty");
    }

    @Test
    void rejectsMarketMismatch() {
        KisMasterSnapshotValidationResult result = validate(
                MarketType.KOSPI,
                parsed(raw(MarketType.KOSDAQ, "000250", "KR7000250001", "삼천당제약",
                        "ST", "0", false, false, false, false)));

        assertThat(result.ready()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("market does not match"));
    }

    @Test
    void rejectsInconsistentSpacRawDomain() {
        KisMasterSnapshotValidationResult result = validate(
                MarketType.KOSPI,
                parsed(raw(MarketType.KOSPI, "000001", "KR7000000001", "잘못된스팩",
                        "EF", "0", true, false, false, false)));

        assertThat(result.ready()).isFalse();
        assertThat(result.errors())
                .contains("SPAC raw fields are inconsistent: 000001");
    }

    @Test
    void rejectsBlankIdentityAndInconsistentCounts() {
        var record = raw(MarketType.KOSPI, "", "", "",
                "", "", false, false, false, false);
        KisMasterParseResult rawResult = new KisMasterParseResult(
                List.of(record), new KisMasterParseSummary(2, 0, 1, 0));

        KisMasterSnapshotValidationResult result = validate(MarketType.KOSPI, rawResult);

        assertThat(result.ready()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("parsed row count"));
        assertThat(result.errors()).anyMatch(error -> error.contains("stockCode"));
        assertThat(result.errors()).anyMatch(error -> error.contains("standardCode"));
        assertThat(result.errors()).anyMatch(error -> error.contains("stockName"));
        assertThat(result.errors()).anyMatch(error -> error.contains("securityGroupCode"));
        assertThat(result.errors()).anyMatch(error -> error.contains("preferredStockCode"));
    }

    private KisMasterSnapshotValidationResult validate(
            MarketType market,
            KisMasterParseResult rawResult
    ) {
        List<KisMasterNormalizedRecord> normalized = rawResult.records().stream()
                .map(this::normalize)
                .toList();
        return validator.validate(market, rawResult, normalized);
    }

    private KisMasterNormalizedRecord normalize(KisMasterRawRecord raw) {
        InstrumentType type = classifier.classify(raw);
        return new KisMasterNormalizedRecord(
                raw.market(), raw.shortCode(), raw.standardCode(), raw.stockName(),
                raw.listingDate(), type, policy.supports(type), raw.securityGroupCode(),
                raw.preferredStockCode(), raw.etpProductCode(), raw.spac(),
                raw.suspended(), raw.liquidationTrading(), raw.managedIssue(), raw);
    }
}
