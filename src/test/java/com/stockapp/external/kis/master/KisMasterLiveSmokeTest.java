package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;

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
            System.out.printf(
                    "%s rows=%d duplicates=%d invalid=%d%n",
                    market,
                    result.summary().parsedRowCount(),
                    result.summary().duplicateShortCodeCount(),
                    result.summary().invalidRowCount());
        }
    }
}
