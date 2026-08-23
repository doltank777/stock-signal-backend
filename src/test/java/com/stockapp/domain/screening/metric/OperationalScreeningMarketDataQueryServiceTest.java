package com.stockapp.domain.screening.metric;

import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockDailyPrice;
import com.stockapp.domain.stock.StockDailyPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalScreeningMarketDataQueryServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 8, 21);

    private StockDailyPriceRepository repository;
    private OperationalScreeningMarketDataQueryService service;
    private Stock stock;

    @BeforeEach
    void setUp() {
        repository = mock(StockDailyPriceRepository.class);
        service = new OperationalScreeningMarketDataQueryService(repository);
        stock = Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .marketType(MarketType.KOSPI)
                .build();
    }

    @Test
    void mapsFinalizedCurrentAndChronologicalHistoryWithOneLimitedQuery() {
        when(repository
                .findByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        eq(stock), eq(D), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        price(D, 250, 200),
                        price(D.minusDays(1), 200, 100),
                        price(D.minusDays(2), 150, 100),
                        price(D.minusDays(3), 100, 100)));

        OperationalScreeningMarketData data = service.load(
                stock, D,
                new OperationalScreeningDataRequirements(3, true));

        assertThat(data.current().currentPrice()).isEqualByComparingTo("250");
        assertThat(data.current().volume()).isEqualByComparingTo("200");
        assertThat(data.current().changeRate()).contains(
                BigDecimal.valueOf(25));
        assertThat(data.history())
                .extracting(value -> value.closePrice())
                .containsExactly(100L, 150L, 200L);
        verify(repository)
                .findByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        eq(stock), eq(D),
                        org.mockito.ArgumentMatchers.argThat(
                                pageable -> pageable.getPageSize() == 4));
    }

    @Test
    void calculatesNegativeChangeRateWithoutRoundingScale() {
        prepareRows(price(D, 90, 10), price(D.minusDays(3), 100, 10));

        OperationalScreeningMarketData data = service.load(
                stock, D,
                new OperationalScreeningDataRequirements(0, true));

        assertThat(data.current().changeRate()).contains(
                BigDecimal.valueOf(-10));
        assertThat(data.history()).isEmpty();
    }

    @Test
    void leavesOnlyChangeRateUnavailableWhenPreviousCloseIsMissingOrZero() {
        prepareRows(price(D, 110, 10));
        OperationalScreeningMarketData missingPrevious = service.load(
                stock, D,
                new OperationalScreeningDataRequirements(0, true));

        prepareRows(price(D, 110, 10), price(D.minusDays(1), 0, 10));
        OperationalScreeningMarketData zeroPrevious = service.load(
                stock, D,
                new OperationalScreeningDataRequirements(0, true));

        assertThat(missingPrevious.current().currentPrice())
                .isEqualByComparingTo("110");
        assertThat(missingPrevious.current().volume())
                .isEqualByComparingTo("10");
        assertThat(missingPrevious.current().changeRate()).isEmpty();
        assertThat(zeroPrevious.current().changeRate()).isEmpty();
    }

    @Test
    void rejectsMissingExactEvaluationDateWithoutOlderFallback() {
        prepareRows(price(D.minusDays(1), 100, 10));

        assertThatThrownBy(() -> service.load(
                stock, D,
                new OperationalScreeningDataRequirements(1, false)))
                .isInstanceOf(OperationalScreeningDataMissingException.class)
                .hasMessageContaining("005930")
                .hasMessageContaining(D.toString());
    }

    @Test
    void preservesInsufficientHistoryAsAvailablePartialHistory() {
        prepareRows(
                price(D, 999, 200),
                price(D.minusDays(1), 200, 100),
                price(D.minusDays(2), 100, 100));

        OperationalScreeningMarketData data = service.load(
                stock, D,
                new OperationalScreeningDataRequirements(20, false));

        assertThat(data.history()).hasSize(2);
        verify(repository)
                .findByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        eq(stock), eq(D),
                        org.mockito.ArgumentMatchers.argThat(
                                pageable -> pageable.getPageSize() == 21));
    }

    private void prepareRows(StockDailyPrice... rows) {
        when(repository
                .findByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        eq(stock), eq(D), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(rows));
    }

    private StockDailyPrice price(
            LocalDate tradeDate, long closePrice, long volume) {
        return StockDailyPrice.builder()
                .stock(stock)
                .tradeDate(tradeDate)
                .openPrice(closePrice)
                .highPrice(closePrice)
                .lowPrice(closePrice)
                .closePrice(closePrice)
                .volume(volume)
                .collectedAt(LocalDateTime.of(2026, 8, 22, 1, 0))
                .build();
    }
}
