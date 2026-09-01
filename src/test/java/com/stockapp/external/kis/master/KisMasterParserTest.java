package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisMasterParserTest {

    private final KisKospiMasterParser kospiParser = new KisKospiMasterParser();
    private final KisKosdaqMasterParser kosdaqParser = new KisKosdaqMasterParser();

    @Test
    void parsesKospiSamsungRecordAndRestoresCp949Name() {
        byte[] content = KisMasterFixture.lines(KisMasterFixture.kospi(
                "005930", "KR7005930003", "삼성전자", "ST", "", "0",
                "N", "N", "N", "N", "19750611"));

        KisMasterParseResult result = kospiParser.parse(content);

        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record.market()).isEqualTo(MarketType.KOSPI);
            assertThat(record.shortCode()).isEqualTo("005930");
            assertThat(record.standardCode()).isEqualTo("KR7005930003");
            assertThat(record.stockName()).isEqualTo("삼성전자");
            assertThat(record.securityGroupCode()).isEqualTo("ST");
            assertThat(record.preferredStockCode()).isEqualTo("0");
            assertThat(record.spac()).isFalse();
            assertThat(record.suspended()).isFalse();
            assertThat(record.liquidationTrading()).isFalse();
            assertThat(record.listingDate()).isEqualTo(LocalDate.of(1975, 6, 11));
            assertThat(record.rawSuffix()).hasSize(227);
            assertThat(record.rawSuffixFields()).hasSize(70);
        });
        assertThat(result.summary()).isEqualTo(new KisMasterParseSummary(1, 0, 0, 0));
    }

    @Test
    void preservesAlphanumericShortCodeAndRawClassificationValues() {
        byte[] content = KisMasterFixture.lines(
                KisMasterFixture.kospi(
                        "0220W0", "KR70220W0000", "한화머시너리앤서비스홀딩스",
                        "ST", "", "0", "N", "N", "N", "N", "20260825"),
                KisMasterFixture.kospi(
                        "0000D0", "KR70000D0000", "ETF테스트",
                        "EF", "2", "0", "N", "N", "N", "N", "20260101"),
                KisMasterFixture.kospi(
                        "Q500061", "KRG500610000", "ETN테스트",
                        "EN", "3", "0", "N", "N", "N", "N", "20260102"),
                KisMasterFixture.kospi(
                        "0030R0", "KR70030R0000", "대신밸류리츠",
                        "RT", "", "0", "N", "N", "N", "N", "20260103"));

        List<KisMasterRawRecord> records = kospiParser.parse(content).records();

        assertThat(records).extracting(KisMasterRawRecord::shortCode)
                .containsExactly("0220W0", "0000D0", "Q500061", "0030R0");
        assertThat(records).extracting(KisMasterRawRecord::securityGroupCode)
                .containsExactly("ST", "EF", "EN", "RT");
        assertThat(records).extracting(KisMasterRawRecord::etpProductCode)
                .containsExactly("", "2", "3", "");
    }

    @Test
    void parsesKosdaqSpacAndStatusFlags() {
        byte[] content = KisMasterFixture.lines(KisMasterFixture.kosdaq(
                "0164H0", "KR70164H0007", "한국제16호스팩", "ST", "", "0",
                "Y", "Y", "N", "Y", "20260630"));

        assertThat(kosdaqParser.parse(content).records()).singleElement().satisfies(record -> {
            assertThat(record.market()).isEqualTo(MarketType.KOSDAQ);
            assertThat(record.spac()).isTrue();
            assertThat(record.suspended()).isTrue();
            assertThat(record.liquidationTrading()).isFalse();
            assertThat(record.managedIssue()).isTrue();
            assertThat(record.rawSuffix()).hasSize(221);
            assertThat(record.rawSuffixFields()).hasSize(64);
        });
    }

    @Test
    void rejectsShortRecord() {
        assertThatThrownBy(() -> kospiParser.parse("too short".getBytes()))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("shorter than required fixed width");
    }

    @Test
    void rejectsMalformedCp949InsteadOfReplacingCharacters() {
        assertThatThrownBy(() -> kospiParser.parse(new byte[]{(byte) 0x81}))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("Failed to decode KIS Master");
    }

    @Test
    void rejectsUnexpectedBooleanCode() {
        byte[] content = KisMasterFixture.lines(KisMasterFixture.kospi(
                "005930", "KR7005930003", "삼성전자", "ST", "", "0",
                "X", "N", "N", "N", "19750611"));

        assertThatThrownBy(() -> kospiParser.parse(content))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("spac must be Y or N");
    }

    @Test
    void rejectsInvalidListingDate() {
        byte[] content = KisMasterFixture.lines(KisMasterFixture.kospi(
                "005930", "KR7005930003", "삼성전자", "ST", "", "0",
                "N", "N", "N", "N", "20260230"));

        assertThatThrownBy(() -> kospiParser.parse(content))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("listingDate must be yyyyMMdd");
    }

    @Test
    void reportsDuplicateCodesWithoutOverwritingRecords() {
        byte[] row = KisMasterFixture.kospi(
                "005930", "KR7005930003", "삼성전자", "ST", "", "0",
                "N", "N", "N", "N", "19750611");

        KisMasterParseResult result = kospiParser.parse(KisMasterFixture.lines(row, row));

        assertThat(result.records()).hasSize(2);
        assertThat(result.summary().duplicateShortCodeCount()).isEqualTo(1);
    }

    @Test
    void routerRejectsKonex() {
        KisMasterParserRouter router = new KisMasterParserRouter(
                List.of(kospiParser, kosdaqParser));

        assertThatThrownBy(() -> router.parse(MarketType.KONEX, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported KIS Master market");
    }
}
