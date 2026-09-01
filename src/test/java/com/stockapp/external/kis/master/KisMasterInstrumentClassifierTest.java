package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KisMasterInstrumentClassifierTest {

    private final KisMasterInstrumentClassifier classifier =
            new KisMasterInstrumentClassifier();

    @ParameterizedTest
    @MethodSource("classifications")
    void classifiesRawInstrumentFields(
            String group,
            String preferred,
            boolean spac,
            InstrumentType expected
    ) {
        assertThat(classifier.classify(raw(group, preferred, spac)))
                .isEqualTo(expected);
    }

    static Stream<Arguments> classifications() {
        return Stream.of(
                Arguments.of("ST", "0", false, InstrumentType.COMMON_STOCK),
                Arguments.of("ST", "0", true, InstrumentType.SPAC),
                Arguments.of("ST", "1", false, InstrumentType.PREFERRED_STOCK),
                Arguments.of("ST", "2", false, InstrumentType.PREFERRED_STOCK),
                Arguments.of("ST", "9", false, InstrumentType.PREFERRED_STOCK),
                Arguments.of("EF", "0", false, InstrumentType.ETF),
                Arguments.of("EN", "0", false, InstrumentType.ETN),
                Arguments.of("RT", "0", false, InstrumentType.REIT),
                Arguments.of("IF", "0", false, InstrumentType.INFRASTRUCTURE_FUND),
                Arguments.of("MF", "0", false, InstrumentType.LISTED_FUND),
                Arguments.of("FS", "0", false, InstrumentType.FOREIGN_STOCK),
                Arguments.of("DR", "0", false, InstrumentType.DEPOSITARY_RECEIPT),
                Arguments.of("BC", "0", false, InstrumentType.BENEFICIARY_CERTIFICATE),
                Arguments.of("PF", "0", false, InstrumentType.FUND_PRODUCT),
                Arguments.of("SR", "0", false, InstrumentType.SUBSCRIPTION_RIGHT),
                Arguments.of("SW", "0", false, InstrumentType.WARRANT),
                Arguments.of("NEW", "0", false, InstrumentType.OTHER));
    }

    @Test
    void failsClosedForUnknownStockSubtype() {
        assertThat(classifier.classify(raw("ST", "X", false)))
                .isEqualTo(InstrumentType.OTHER);
    }

    private static KisMasterRawRecord raw(
            String group,
            String preferred,
            boolean spac
    ) {
        return new KisMasterRawRecord(
                MarketType.KOSPI,
                "005930",
                "KR7005930003",
                "삼성전자",
                group,
                preferred,
                "",
                spac,
                false,
                false,
                false,
                LocalDate.of(1975, 6, 11),
                "",
                List.of());
    }
}
