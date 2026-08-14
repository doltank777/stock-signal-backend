package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMarketDataQueryServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 14);

    @Mock
    private StockPriceRepository stockPriceRepository;

    @Mock
    private StockDailyPriceRepository stockDailyPriceRepository;

    private StockMarketDataQueryService service;
    private Stock stock;

    @BeforeEach
    void setUp() {
        service = new StockMarketDataQueryService(
                stockPriceRepository, stockDailyPriceRepository);
        stock = Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build();
    }

    @Test
    void mapsLatestSnapshotForRequestedDate() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 14, 11, 0);
        StockPrice price = StockPrice.builder()
                .id(10L)
                .stockCode("005930")
                .tradeDate(BASE_DATE)
                .currentPrice(71_000L)
                .changeRate(1.25)
                .volume(12_345_678L)
                .collectedAt(collectedAt)
                .build();
        when(stockPriceRepository
                .findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
                        "005930", BASE_DATE))
                .thenReturn(Optional.of(price));

        Optional<LatestStockSnapshot> result =
                service.findLatestSnapshotForDate(stock, BASE_DATE);

        assertThat(result).contains(new LatestStockSnapshot(
                "005930", BASE_DATE, 71_000L, 1.25,
                12_345_678L, collectedAt));
        verify(stockPriceRepository)
                .findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
                        "005930", BASE_DATE);
        verify(stockPriceRepository, never())
                .findTopByStockCodeOrderByCollectedAtDesc("005930");
    }

    @Test
    void returnsEmptyWhenSnapshotDoesNotExistForRequestedDate() {
        when(stockPriceRepository
                .findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
                        "005930", BASE_DATE))
                .thenReturn(Optional.empty());

        assertThat(service.findLatestSnapshotForDate(stock, BASE_DATE))
                .isEmpty();
    }

    @Test
    void rejectsNullStockForSnapshot() {
        assertThatThrownBy(() ->
                service.findLatestSnapshotForDate(null, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("종목은 필수입니다.");

        verifyNoInteractions(stockPriceRepository, stockDailyPriceRepository);
    }

    @Test
    void rejectsNullBaseDateForSnapshot() {
        assertThatThrownBy(() ->
                service.findLatestSnapshotForDate(stock, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 기준일은 필수입니다.");

        verifyNoInteractions(stockPriceRepository, stockDailyPriceRepository);
    }

    @Test
    void returnsDailyHistoryInAscendingOrderAndMapsRequiredFields() {
        List<StockDailyPrice> descending = List.of(
                dailyPrice("2026-08-13", 73_000L, 13_000L),
                dailyPrice("2026-08-12", 72_000L, 12_000L),
                dailyPrice("2026-08-11", 71_000L, 11_000L));
        when(stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock, BASE_DATE, PageRequest.of(0, 3)))
                .thenReturn(descending);

        List<DailyPriceData> result =
                service.findRecentDailyPricesBefore(stock, BASE_DATE, 3);

        assertThat(result).containsExactly(
                new DailyPriceData(LocalDate.of(2026, 8, 11), 71_000L, 11_000L),
                new DailyPriceData(LocalDate.of(2026, 8, 12), 72_000L, 12_000L),
                new DailyPriceData(LocalDate.of(2026, 8, 13), 73_000L, 13_000L));
        verify(stockDailyPriceRepository)
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock, BASE_DATE, PageRequest.of(0, 3));
    }

    @Test
    void returnsAvailableHistoryWhenFewerThanPeriodExist() {
        when(stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock, BASE_DATE, PageRequest.of(0, 5)))
                .thenReturn(List.of(
                        dailyPrice("2026-08-13", 73_000L, 13_000L),
                        dailyPrice("2026-08-12", 72_000L, 12_000L)));

        assertThat(service.findRecentDailyPricesBefore(stock, BASE_DATE, 5))
                .extracting(DailyPriceData::tradeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 13));
    }

    @Test
    void returnsEmptyListWhenDailyHistoryDoesNotExist() {
        when(stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock, BASE_DATE, PageRequest.of(0, 20)))
                .thenReturn(List.of());

        assertThat(service.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .isEmpty();
    }

    @Test
    void acceptsPeriodOneAndUsesMatchingPageRequest() {
        StockDailyPrice latest = dailyPrice("2026-08-13", 73_000L, 13_000L);
        when(stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock, BASE_DATE, PageRequest.of(0, 1)))
                .thenReturn(List.of(latest));

        assertThat(service.findRecentDailyPricesBefore(stock, BASE_DATE, 1))
                .containsExactly(new DailyPriceData(
                        LocalDate.of(2026, 8, 13), 73_000L, 13_000L));
        verify(stockDailyPriceRepository)
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock, BASE_DATE, PageRequest.of(0, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidPeriod(int period) {
        assertThatThrownBy(() ->
                service.findRecentDailyPricesBefore(stock, BASE_DATE, period))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 기간은 1 이상이어야 합니다.");

        verifyNoInteractions(stockPriceRepository, stockDailyPriceRepository);
    }

    @Test
    void rejectsNullStockForDailyHistory() {
        assertThatThrownBy(() ->
                service.findRecentDailyPricesBefore(null, BASE_DATE, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("종목은 필수입니다.");

        verifyNoInteractions(stockPriceRepository, stockDailyPriceRepository);
    }

    @Test
    void rejectsNullBaseDateForDailyHistory() {
        assertThatThrownBy(() ->
                service.findRecentDailyPricesBefore(stock, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 기준일은 필수입니다.");

        verifyNoInteractions(stockPriceRepository, stockDailyPriceRepository);
    }

    private StockDailyPrice dailyPrice(
            String tradeDate,
            long closePrice,
            long volume
    ) {
        return StockDailyPrice.builder()
                .stock(stock)
                .tradeDate(LocalDate.parse(tradeDate))
                .openPrice(closePrice)
                .highPrice(closePrice)
                .lowPrice(closePrice)
                .closePrice(closePrice)
                .volume(volume)
                .build();
    }
}
