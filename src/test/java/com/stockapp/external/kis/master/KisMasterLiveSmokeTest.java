package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "KIS_MASTER_LIVE", matches = "true")
class KisMasterLiveSmokeTest {

    @Test
    void downloadsAndParsesCurrentKospiAndKosdaqMasters() {
        KisMasterClient client = new KisMasterClient(
                new KisMasterDownloader(RestClient.builder()),
                new KisMasterArchiveReader(),
                new KisMasterParserRouter(List.of(
                        new KisKospiMasterParser(),
                        new KisKosdaqMasterParser())));
        KisMasterSnapshotFactory snapshotFactory = new KisMasterSnapshotFactory(
                new KisMasterInstrumentClassifier(),
                new KisMasterInstrumentPolicy(),
                new KisMasterSnapshotValidator(),
                Clock.systemUTC());

        for (MarketType market : List.of(MarketType.KOSPI, MarketType.KOSDAQ)) {
            KisMasterParseResult result = client.downloadAndParse(market);
            assertThat(result.records()).isNotEmpty();
            assertThat(result.records()).allSatisfy(record -> {
                assertThat(record.shortCode()).isNotBlank();
                assertThat(record.standardCode()).isNotBlank();
                assertThat(record.stockName()).isNotBlank();
                assertThat(record.market()).isEqualTo(market);
            });
            assertThat(result.summary().duplicateShortCodeCount()).isZero();
            assertThat(result.summary().blankCodeCount()).isZero();
            assertThat(result.summary().invalidRowCount()).isZero();
            KisMasterSnapshot snapshot = snapshotFactory.create(market, result);
            assertThat(snapshot.publishable()).isTrue();
            Map<InstrumentType, Integer> distribution = new EnumMap<>(InstrumentType.class);
            for (KisMasterNormalizedRecord record : snapshot.records()) {
                distribution.merge(record.instrumentType(), 1, Integer::sum);
            }
            System.out.printf(
                    "%s total=%d supported=%d unsupported=%d duplicates=%d invalid=%d "
                            + "types=%s unknownGroups=%s%n",
                    market,
                    result.summary().parsedRowCount(),
                    snapshot.supportedInstrumentCount(),
                    snapshot.unsupportedInstrumentCount(),
                    result.summary().duplicateShortCodeCount(),
                    result.summary().invalidRowCount(),
                    distribution,
                    snapshot.validation().unknownSecurityGroupCodes());
        }
    }
}
