package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisMasterArchiveReaderTest {

    private final KisMasterArchiveReader reader = new KisMasterArchiveReader();

    @Test
    void readsExpectedEntryWithoutExtractingToDisk() {
        byte[] content = "master-content".getBytes();
        byte[] archive = KisMasterFixture.zip("kospi_code.mst", content);

        assertThat(reader.readMasterEntry(MarketType.KOSPI, archive)).isEqualTo(content);
    }

    @Test
    void rejectsInvalidZip() {
        assertThatThrownBy(() -> reader.readMasterEntry(
                MarketType.KOSPI, "not-a-zip".getBytes()))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("Invalid KIS Master ZIP");
    }

    @Test
    void rejectsArchiveWithoutExpectedEntry() {
        byte[] archive = KisMasterFixture.zip("other.txt", "content".getBytes());

        assertThatThrownBy(() -> reader.readMasterEntry(MarketType.KOSDAQ, archive))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("Expected KIS Master entry is missing")
                .hasMessageContaining("kosdaq_code.mst");
    }

    @Test
    void rejectsEmptyExpectedEntry() {
        byte[] archive = KisMasterFixture.zip("kospi_code.mst", new byte[0]);

        assertThatThrownBy(() -> reader.readMasterEntry(MarketType.KOSPI, archive))
                .isInstanceOf(KisMasterException.class)
                .hasMessageContaining("entry is empty");
    }
}
