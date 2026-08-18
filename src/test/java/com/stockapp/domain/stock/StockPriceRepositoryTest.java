package com.stockapp.domain.stock;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StockPriceRepositoryTest {

    @Autowired
    private StockPriceRepository stockPriceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsLatestSnapshotForStockCodeAndTradeDate() {
        LocalDate baseDate = LocalDate.of(2026, 8, 14);
        StockPrice nineOClock = savePrice("005930", baseDate);
        StockPrice tenOClock = savePrice("005930", baseDate);
        StockPrice elevenOClock = savePrice("005930", baseDate);
        StockPrice staleWithLaterCollection = savePrice(
                "005930", baseDate.minusDays(1));
        StockPrice otherStock = savePrice("000660", baseDate);

        updateCollectedAt(nineOClock, LocalDateTime.of(2026, 8, 14, 9, 0));
        updateCollectedAt(tenOClock, LocalDateTime.of(2026, 8, 14, 10, 0));
        updateCollectedAt(elevenOClock, LocalDateTime.of(2026, 8, 14, 11, 0));
        updateCollectedAt(staleWithLaterCollection,
                LocalDateTime.of(2026, 8, 14, 12, 0));
        updateCollectedAt(otherStock, LocalDateTime.of(2026, 8, 14, 13, 0));
        entityManager.clear();

        assertThat(stockPriceRepository
                .findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
                        "005930", baseDate))
                .get()
                .extracting(StockPrice::getId)
                .isEqualTo(elevenOClock.getId());
    }

    @Test
    void returnsEmptyWhenSnapshotDoesNotExistForTradeDate() {
        savePrice("005930", LocalDate.of(2026, 8, 13));

        assertThat(stockPriceRepository
                .findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
                        "005930", LocalDate.of(2026, 8, 14)))
                .isEmpty();
    }

    @Test
    void usesHigherIdWhenCollectedAtIsEqual() {
        LocalDate baseDate = LocalDate.of(2026, 8, 14);
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 14, 10, 0);
        StockPrice first = savePrice("005930", baseDate);
        StockPrice second = savePrice("005930", baseDate);

        updateCollectedAt(first, collectedAt);
        updateCollectedAt(second, collectedAt);
        entityManager.clear();

        assertThat(stockPriceRepository
                .findTopByStockCodeAndTradeDateOrderByCollectedAtDescIdDesc(
                        "005930", baseDate))
                .get()
                .extracting(StockPrice::getId)
                .isEqualTo(second.getId());
    }

    @Test
    void returnsLatestTradeDateAcrossSnapshots() {
        savePrice("005930", LocalDate.of(2026, 8, 12));
        savePrice("000660", LocalDate.of(2026, 8, 14));
        savePrice("035420", LocalDate.of(2026, 8, 13));

        assertThat(stockPriceRepository.findLatestTradeDate())
                .contains(LocalDate.of(2026, 8, 14));
    }

    @Test
    void returnsEmptyLatestTradeDateWithoutSnapshots() {
        assertThat(stockPriceRepository.findLatestTradeDate()).isEmpty();
    }

    private StockPrice savePrice(String stockCode, LocalDate tradeDate) {
        return stockPriceRepository.saveAndFlush(StockPrice.builder()
                .stockCode(stockCode)
                .currentPrice(70_000L)
                .changeRate(1.5)
                .volume(10_000_000L)
                .tradeDate(tradeDate)
                .build());
    }

    private void updateCollectedAt(
            StockPrice stockPrice,
            LocalDateTime collectedAt
    ) {
        entityManager.createQuery("""
                        update StockPrice price
                        set price.collectedAt = :collectedAt
                        where price.id = :id
                        """)
                .setParameter("collectedAt", collectedAt)
                .setParameter("id", stockPrice.getId())
                .executeUpdate();
    }
}
