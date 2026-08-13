package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockDailyPriceServiceTest {

    private static final LocalDate START_DATE =
            LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE =
            LocalDate.of(2026, 8, 13);

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockDailyPriceRepository stockDailyPriceRepository;

    @Mock
    private KisDailyPriceClient kisDailyPriceClient;

    @Captor
    private ArgumentCaptor<List<StockDailyPrice>> dailyPricesCaptor;

    private StockDailyPriceService stockDailyPriceService;
    private Stock stock;

    @BeforeEach
    void setUp() {
        stockDailyPriceService = new StockDailyPriceService(
                stockRepository,
                kisDailyPriceClient,
                new StockDailyPriceWriter(stockDailyPriceRepository));
        stock = Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build();

        when(stockRepository.findByStockCode("005930"))
                .thenReturn(Optional.of(stock));
    }

    @Test
    void savesNewDailyPricesAndMapsEntityFields() {
        List<KisDailyPrice> prices = createThreePrices();
        when(kisDailyPriceClient.getDailyPrices(
                "005930", START_DATE, END_DATE))
                .thenReturn(prices);
        StockDailyPriceSaveResult result = stockDailyPriceService
                .saveDailyPrices("005930", START_DATE, END_DATE);

        verify(stockDailyPriceRepository).saveAll(
                dailyPricesCaptor.capture());

        assertThat(result.getRequestedCount()).isEqualTo(3);
        assertThat(result.getSavedCount()).isEqualTo(3);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(dailyPricesCaptor.getValue()).hasSize(3);

        StockDailyPrice saved = dailyPricesCaptor.getValue().getFirst();
        KisDailyPrice source = prices.getFirst();
        assertThat(saved.getStock()).isSameAs(stock);
        assertThat(saved.getTradeDate()).isEqualTo(source.getTradeDate());
        assertThat(saved.getOpenPrice()).isEqualTo(source.getOpenPrice());
        assertThat(saved.getHighPrice()).isEqualTo(source.getHighPrice());
        assertThat(saved.getLowPrice()).isEqualTo(source.getLowPrice());
        assertThat(saved.getClosePrice()).isEqualTo(source.getClosePrice());
        assertThat(saved.getVolume()).isEqualTo(source.getVolume());
        assertThat(saved.getCollectedAt()).isNull();
    }

    @Test
    void skipsExistingDailyPriceAndSavesOnlyNewPrices() {
        List<KisDailyPrice> prices = createThreePrices();
        when(kisDailyPriceClient.getDailyPrices(
                "005930", START_DATE, END_DATE))
                .thenReturn(prices);
        when(stockDailyPriceRepository.findTradeDates(
                stock, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
                .thenReturn(List.of(prices.get(0).getTradeDate()));

        StockDailyPriceSaveResult result = stockDailyPriceService
                .saveDailyPrices("005930", START_DATE, END_DATE);

        verify(stockDailyPriceRepository).saveAll(
                dailyPricesCaptor.capture());
        assertThat(dailyPricesCaptor.getValue()).hasSize(2);
        assertThat(result.getRequestedCount()).isEqualTo(3);
        assertThat(result.getSavedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(1);
    }

    @Test
    void skipsAllExistingDailyPricesWithoutSaving() {
        List<KisDailyPrice> prices = createThreePrices();
        when(kisDailyPriceClient.getDailyPrices(
                "005930", START_DATE, END_DATE))
                .thenReturn(prices);
        when(stockDailyPriceRepository.findTradeDates(
                stock, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)))
                .thenReturn(prices.stream().map(KisDailyPrice::getTradeDate).toList());

        StockDailyPriceSaveResult result = stockDailyPriceService
                .saveDailyPrices("005930", START_DATE, END_DATE);

        verify(stockDailyPriceRepository, never()).saveAll(any());
        assertThat(result.getRequestedCount()).isEqualTo(3);
        assertThat(result.getSavedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(3);
    }

    @Test
    void handlesEmptyResponseWithoutSaving() {
        when(kisDailyPriceClient.getDailyPrices(
                "005930", START_DATE, END_DATE))
                .thenReturn(List.of());

        StockDailyPriceSaveResult result = stockDailyPriceService
                .saveDailyPrices("005930", START_DATE, END_DATE);

        verify(stockDailyPriceRepository, never()).saveAll(any());
        assertThat(result.getRequestedCount()).isZero();
        assertThat(result.getSavedCount()).isZero();
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    void skipsDuplicateTradeDateWithinSameResponse() {
        KisDailyPrice price = createPrice(LocalDate.of(2026, 8, 12), 100L);
        when(kisDailyPriceClient.getDailyPrices(
                "005930", START_DATE, END_DATE))
                .thenReturn(List.of(price, price));

        StockDailyPriceSaveResult result = stockDailyPriceService
                .saveDailyPrices("005930", START_DATE, END_DATE);

        verify(stockDailyPriceRepository).saveAll(
                dailyPricesCaptor.capture());
        assertThat(dailyPricesCaptor.getValue()).hasSize(1);
        assertThat(result.getRequestedCount()).isEqualTo(2);
        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
    }

    private List<KisDailyPrice> createThreePrices() {
        return List.of(
                createPrice(LocalDate.of(2026, 8, 12), 100L),
                createPrice(LocalDate.of(2026, 8, 11), 200L),
                createPrice(LocalDate.of(2026, 8, 10), 300L));
    }

    private KisDailyPrice createPrice(
            LocalDate tradeDate,
            long offset) {

        return KisDailyPrice.builder()
                .tradeDate(tradeDate)
                .openPrice(70_000L + offset)
                .highPrice(72_000L + offset)
                .lowPrice(69_000L + offset)
                .closePrice(71_000L + offset)
                .volume(10_000_000L + offset)
                .build();
    }
}
