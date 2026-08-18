package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.stock.StockPriceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeScreeningBaseDateProviderTest {

    @Test
    void returnsLatestPersistedSnapshotTradeDate() {
        StockPriceRepository repository = mock(StockPriceRepository.class);
        LocalDate latestDate = LocalDate.of(2026, 8, 13);
        when(repository.findLatestTradeDate()).thenReturn(
                Optional.of(latestDate));
        RealtimeScreeningBaseDateProvider provider =
                new RealtimeScreeningBaseDateProvider(repository);

        assertThat(provider.latestBaseDate()).isEqualTo(latestDate);
    }

    @Test
    void failsWhenNoSnapshotExists() {
        StockPriceRepository repository = mock(StockPriceRepository.class);
        when(repository.findLatestTradeDate()).thenReturn(Optional.empty());
        RealtimeScreeningBaseDateProvider provider =
                new RealtimeScreeningBaseDateProvider(repository);

        assertThatIllegalStateException()
                .isThrownBy(provider::latestBaseDate)
                .withMessage(
                        "no stock price snapshot found for realtime screening");
    }
}
