package com.stockapp.domain.signal;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class SignalRepositoryTest {

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private SearchConditionRepository searchConditionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void checksCooldownByStockAndSearchConditionIndependently() {
        Stock stock = stockRepository.save(Stock.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build());
        SearchCondition first = searchConditionRepository.save(condition("first"));
        SearchCondition second = searchConditionRepository.save(condition("second"));
        LocalDateTime detectedAt = LocalDateTime.of(2026, 8, 19, 10, 0);
        signalRepository.saveAndFlush(Signal.createSearchConditionMatch(
                stock, first, detectedAt));
        entityManager.clear();

        assertThat(signalRepository
                .existsByStockAndSearchConditionAndDetectedAtAfter(
                        stock, first, detectedAt.minusMinutes(30)))
                .isTrue();
        assertThat(signalRepository
                .existsByStockAndSearchConditionAndDetectedAtAfter(
                        stock, first, detectedAt.plusSeconds(1)))
                .isFalse();
        assertThat(signalRepository
                .existsByStockAndSearchConditionAndDetectedAtAfter(
                        stock, second, detectedAt.minusMinutes(30)))
                .isFalse();
    }

    @Test
    void fetchesStockAndRequiredSearchConditionInDetectedAtDescendingOrder() {
        Stock stock = stockRepository.saveAndFlush(Stock.builder()
                .stockCode("035420")
                .stockName("NAVER")
                .marketType(MarketType.KOSPI)
                .build());
        SearchCondition first = searchConditionRepository.saveAndFlush(
                condition("first"));
        SearchCondition second = searchConditionRepository.saveAndFlush(
                condition("second"));
        signalRepository.saveAndFlush(Signal.createSearchConditionMatch(
                stock,
                first,
                LocalDateTime.of(2026, 8, 19, 9, 0)));
        signalRepository.saveAndFlush(Signal.createSearchConditionMatch(
                stock,
                second,
                LocalDateTime.of(2026, 8, 19, 10, 0)));
        entityManager.clear();

        var signals = signalRepository.findAllWithStockOrderByDetectedAtDesc();

        assertThat(signals)
                .extracting(signal -> signal.getSearchCondition().getName())
                .containsExactly("second", "first");
        assertThat(Hibernate.isInitialized(signals.get(0).getStock())).isTrue();
        assertThat(Hibernate.isInitialized(signals.get(0).getSearchCondition())).isTrue();
        assertThat(Hibernate.isInitialized(signals.get(1).getStock())).isTrue();
        assertThat(Hibernate.isInitialized(signals.get(1).getSearchCondition())).isTrue();
    }

    @Test
    void rejectsMissingSearchCondition() {
        Stock stock = Stock.builder()
                .stockCode("000660")
                .stockName("SK하이닉스")
                .marketType(MarketType.KOSPI)
                .build();

        assertThatThrownBy(() -> Signal.createSearchConditionMatch(
                stock, null, LocalDateTime.of(2026, 8, 19, 10, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("searchCondition is required");
    }

    private SearchCondition condition(String name) {
        return SearchCondition.create(
                name, null, true, 100, 80, true, null);
    }
}
