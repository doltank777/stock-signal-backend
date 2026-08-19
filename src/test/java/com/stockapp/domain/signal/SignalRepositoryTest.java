package com.stockapp.domain.signal;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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
    void readsEveryLegacyEnumWithNullSearchCondition() {
        Stock stock = stockRepository.saveAndFlush(Stock.builder()
                .stockCode("000660")
                .stockName("SK하이닉스")
                .marketType(MarketType.KOSPI)
                .build());
        insertLegacySignal(stock.getId(), "VOLUME_SPIKE", "거래량 급증 신호 발생");
        insertLegacySignal(
                stock.getId(),
                "MOVING_AVERAGE_BREAKOUT",
                "이동평균 돌파 신호 발생");
        entityManager.clear();

        assertThat(signalRepository.findAll())
                .allSatisfy(signal ->
                        assertThat(signal.getSearchCondition()).isNull())
                .extracting(Signal::getSignalType)
                .containsExactlyInAnyOrder(
                        SignalType.VOLUME_SPIKE,
                        SignalType.MOVING_AVERAGE_BREAKOUT);
    }

    private void insertLegacySignal(
            Long stockId,
            String signalType,
            String message) {
        entityManager.createNativeQuery("""
                        INSERT INTO signals (
                            stock_id, search_condition_id, signal_type, message,
                            base_value, current_value, change_rate, detected_at
                        ) VALUES (?, NULL, ?, ?, 100, 300, 3.0, ?)
                        """)
                .setParameter(1, stockId)
                .setParameter(2, signalType)
                .setParameter(3, message)
                .setParameter(4, LocalDateTime.of(2026, 8, 19, 10, 0))
                .executeUpdate();
    }

    private SearchCondition condition(String name) {
        return SearchCondition.create(
                name, null, true, 100, 80, true, null);
    }
}
