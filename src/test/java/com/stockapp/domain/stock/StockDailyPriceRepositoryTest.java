package com.stockapp.domain.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class StockDailyPriceRepositoryTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockDailyPriceRepository stockDailyPriceRepository;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = stockRepository.save(Stock.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build());
    }

    @Test
    void rejectsDuplicateStockAndTradeDate() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 12);

        stockDailyPriceRepository.saveAndFlush(
                createDailyPrice(tradeDate));

        assertThatThrownBy(() -> stockDailyPriceRepository.saveAndFlush(
                createDailyPrice(tradeDate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnsLatestLimitedPricesBeforeBaseDate() {
        List.of(
                LocalDate.of(2026, 8, 8),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14)
        ).forEach(tradeDate -> stockDailyPriceRepository.save(
                createDailyPrice(tradeDate)));
        stockDailyPriceRepository.flush();

        List<StockDailyPrice> prices = stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock,
                        LocalDate.of(2026, 8, 14),
                        PageRequest.of(0, 3));

        assertThat(prices)
                .extracting(StockDailyPrice::getTradeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 13),
                        LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 11));
    }

    @Test
    void returnsEvaluationDateAndLimitedPreviousRows() {
        List.of(
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14)
        ).forEach(tradeDate -> stockDailyPriceRepository.save(
                createDailyPrice(tradeDate)));
        stockDailyPriceRepository.flush();

        List<StockDailyPrice> prices = stockDailyPriceRepository
                .findByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        stock,
                        LocalDate.of(2026, 8, 14),
                        PageRequest.of(0, 3));

        assertThat(prices)
                .extracting(StockDailyPrice::getTradeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 8, 13),
                        LocalDate.of(2026, 8, 12));
    }

    @Test
    void returnsAllAvailablePricesWhenFewerThanRequestedExist() {
        List.of(
                LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 13)
        ).forEach(date -> stockDailyPriceRepository.save(createDailyPrice(date)));

        List<StockDailyPrice> prices = stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock,
                        LocalDate.of(2026, 8, 14),
                        PageRequest.of(0, 5));

        assertThat(prices)
                .extracting(StockDailyPrice::getTradeDate)
                .containsExactly(
                        LocalDate.of(2026, 8, 13),
                        LocalDate.of(2026, 8, 12));
    }

    @Test
    void returnsEmptyListWhenHistoryDoesNotExist() {
        assertThat(stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock,
                        LocalDate.of(2026, 8, 14),
                        PageRequest.of(0, 5)))
                .isEmpty();
    }

    @Test
    void returnsOneLatestPriceBeforeWeekendBaseDate() {
        List.of(
                LocalDate.of(2026, 8, 13),
                LocalDate.of(2026, 8, 14)
        ).forEach(date -> stockDailyPriceRepository.save(createDailyPrice(date)));

        List<StockDailyPrice> prices = stockDailyPriceRepository
                .findByStockAndTradeDateBeforeOrderByTradeDateDesc(
                        stock,
                        LocalDate.of(2026, 8, 16),
                        PageRequest.of(0, 1));

        assertThat(prices)
                .extracting(StockDailyPrice::getTradeDate)
                .containsExactly(LocalDate.of(2026, 8, 14));
    }

    @Test
    void checksExistingPriceByStockAndTradeDate() {
        LocalDate tradeDate = LocalDate.of(2026, 8, 12);
        stockDailyPriceRepository.saveAndFlush(
                createDailyPrice(tradeDate));

        assertThat(stockDailyPriceRepository
                .existsByStockAndTradeDate(stock, tradeDate))
                .isTrue();
        assertThat(stockDailyPriceRepository
                .existsByStockAndTradeDate(
                        stock,
                        LocalDate.of(2026, 8, 11)))
                .isFalse();
    }

    @Test
    void countsAndSelectsDatesUpToBaseDate() {
        List.of(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 14)).forEach(date ->
                stockDailyPriceRepository.save(createDailyPrice(date)));
        stockDailyPriceRepository.flush();

        assertThat(stockDailyPriceRepository.countByStockAndTradeDateLessThanEqual(
                stock, LocalDate.of(2026, 8, 12))).isEqualTo(2);
        assertThat(stockDailyPriceRepository.findTradeDates(
                stock, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 14)))
                .containsExactly(
                        LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 14));
    }

    @Test
    void findsOnlyTargetStockDatesInsideInclusiveRangeInAscendingOrder() {
        Stock otherStock = stockRepository.save(Stock.builder()
                .stockCode("035720")
                .stockName("Kakao")
                .marketType(MarketType.KOSDAQ)
                .build());
        stockDailyPriceRepository.saveAll(List.of(
                createDailyPrice(stock, LocalDate.of(2026, 8, 9)),
                createDailyPrice(stock, LocalDate.of(2026, 8, 12)),
                createDailyPrice(stock, LocalDate.of(2026, 8, 10)),
                createDailyPrice(stock, LocalDate.of(2026, 8, 14)),
                createDailyPrice(stock, LocalDate.of(2026, 8, 15)),
                createDailyPrice(otherStock, LocalDate.of(2026, 8, 11))));
        stockDailyPriceRepository.flush();

        assertThat(stockDailyPriceRepository.findTradeDates(
                stock, LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 14)))
                .containsExactly(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 14));
    }

    @Test
    void returnsLatestTradeDateOrEmpty() {
        assertThat(stockDailyPriceRepository.findLatestTradeDateByStock(stock))
                .isEmpty();

        List.of(
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 14),
                LocalDate.of(2026, 8, 12)
        ).forEach(date -> stockDailyPriceRepository.save(createDailyPrice(date)));
        stockDailyPriceRepository.flush();

        assertThat(stockDailyPriceRepository.findLatestTradeDateByStock(stock))
                .contains(LocalDate.of(2026, 8, 14));
    }

    @Test
    void bulkLookupReturnsOnlyRequestedStocksWithExactDateRows() {
        LocalDate evaluationDate = LocalDate.of(2026, 8, 14);
        Stock requestedWithExactRow = stock;
        Stock requestedWithOldRow = stockRepository.save(Stock.builder()
                .stockCode("035720")
                .stockName("Kakao")
                .marketType(MarketType.KOSDAQ)
                .build());
        Stock extraKonex = stockRepository.save(Stock.builder()
                .stockCode("950000")
                .stockName("Konex")
                .marketType(MarketType.KONEX)
                .build());
        stockDailyPriceRepository.saveAll(List.of(
                createDailyPrice(requestedWithExactRow, evaluationDate),
                createDailyPrice(requestedWithOldRow,
                        evaluationDate.minusDays(1)),
                createDailyPrice(extraKonex, evaluationDate)));
        stockDailyPriceRepository.flush();

        List<Long> result = stockDailyPriceRepository
                .findStockIdsWithPriceOnDate(
                        evaluationDate,
                        List.of(requestedWithExactRow.getId(),
                                requestedWithOldRow.getId()));

        assertThat(result).containsExactly(requestedWithExactRow.getId());
    }

    private StockDailyPrice createDailyPrice(LocalDate tradeDate) {
        return createDailyPrice(stock, tradeDate);
    }

    private StockDailyPrice createDailyPrice(
            Stock targetStock, LocalDate tradeDate) {
        return StockDailyPrice.builder()
                .stock(targetStock)
                .tradeDate(tradeDate)
                .openPrice(70_000L)
                .highPrice(72_000L)
                .lowPrice(69_000L)
                .closePrice(71_000L)
                .volume(10_000_000L)
                .build();
    }
}
