package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalStockUniverseServiceTest {

    private final StockRepository stockRepository = mock(StockRepository.class);
    private final SupportedInstrumentPolicy supportedInstrumentPolicy =
            new SupportedInstrumentPolicy();
    private final OperationalStockUniverseService service =
            new OperationalStockUniverseService(
                    stockRepository, supportedInstrumentPolicy);

    @Test
    void returnsImmutableHistoryTargetsInRepositoryOrder() {
        Stock first = stock(1L, "000001");
        Stock second = stock(2L, "000002");
        when(stockRepository.findHistoryEligibleStocks(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ),
                supportedInstrumentPolicy.supportedTypes()))
                .thenReturn(List.of(first, second));

        List<Stock> result = service.findHistoryTargets();

        assertThat(result).containsExactly(first, second);
        assertThatThrownBy(() -> result.add(stock(3L, "000003")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsImmutableCurrentTargetsInRepositoryOrder() {
        Stock first = stock(1L, "000001");
        when(stockRepository.findCurrentEligibleStocks(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ),
                supportedInstrumentPolicy.supportedTypes()))
                .thenReturn(List.of(first));

        List<Stock> result = service.findCurrentTargets();

        assertThat(result).containsExactly(first);
        assertThatThrownBy(result::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Stock stock(Long id, String code) {
        return Stock.builder()
                .id(id)
                .stockCode(code)
                .stockName("Stock " + code)
                .marketType(MarketType.KOSPI)
                .build();
    }
}
