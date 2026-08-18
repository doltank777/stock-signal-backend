package com.stockapp.domain.signal.metric;

import com.stockapp.domain.stock.StockDailyPrice;
import com.stockapp.domain.stock.StockDailyPriceRepository;
import com.stockapp.domain.stock.dto.DailyPriceData;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeDailyHistoryLoaderTest {

    @Test
    void loadsOnlyRequestedHistoryBeforeTradeDateAndReturnsAscendingData() {
        StockDailyPriceRepository repository = mock(
                StockDailyPriceRepository.class);
        LocalDate tradeDate = LocalDate.of(2026, 8, 18);
        StockDailyPrice recent = daily(
                LocalDate.of(2026, 8, 17), 200L, 2_000L);
        StockDailyPrice older = daily(
                LocalDate.of(2026, 8, 16), 100L, 1_000L);
        when(repository.findByStockIdAndTradeDateBeforeOrderByTradeDateDesc(
                1L, tradeDate, PageRequest.of(0, 20)))
                .thenReturn(List.of(recent, older));
        RealtimeDailyHistoryLoader loader =
                new RealtimeDailyHistoryLoader(repository);

        List<DailyPriceData> result = loader.load(1L, tradeDate, 20);

        assertThat(result)
                .extracting(DailyPriceData::tradeDate)
                .containsExactly(older.getTradeDate(), recent.getTradeDate());
        verify(repository)
                .findByStockIdAndTradeDateBeforeOrderByTradeDateDesc(
                        1L, tradeDate, PageRequest.of(0, 20));
    }

    private StockDailyPrice daily(
            LocalDate tradeDate,
            Long closePrice,
            Long volume
    ) {
        return StockDailyPrice.builder()
                .tradeDate(tradeDate)
                .closePrice(closePrice)
                .volume(volume)
                .build();
    }
}
