package com.stockapp.domain.signal.dto;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.signal.Signal;
import com.stockapp.domain.signal.SignalRepository;
import com.stockapp.domain.signal.SignalType;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SignalResponseTest {

    private static final LocalDateTime DETECTED_AT =
            LocalDateTime.of(2026, 8, 19, 10, 0);

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private SearchConditionRepository searchConditionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void convertsSearchConditionSignalWithConditionAndNullMetrics() {
        Stock stock = saveStock("005930", "삼성전자");
        SearchCondition searchCondition = searchConditionRepository.saveAndFlush(
                SearchCondition.create(
                        "거래량 증가 + 이동평균 돌파",
                        null,
                        true,
                        100,
                        80,
                        true,
                        null));
        signalRepository.saveAndFlush(Signal.createSearchConditionMatch(
                stock, searchCondition, DETECTED_AT));
        entityManager.clear();

        Signal signal = signalRepository
                .findAllWithStockOrderByDetectedAtDesc()
                .getFirst();
        SignalResponse response = SignalResponse.from(signal);

        assertThat(response.getId()).isEqualTo(signal.getId());
        assertThat(response.getStockCode()).isEqualTo("005930");
        assertThat(response.getStockName()).isEqualTo("삼성전자");
        assertThat(response.getSearchConditionId()).isEqualTo(searchCondition.getId());
        assertThat(response.getSearchConditionName())
                .isEqualTo("거래량 증가 + 이동평균 돌파");
        assertThat(response.getSignalType()).isEqualTo(
                SignalType.SEARCH_CONDITION_MATCH);
        assertThat(response.getBaseValue()).isNull();
        assertThat(response.getCurrentValue()).isNull();
        assertThat(response.getChangeRate()).isNull();
        assertThat(response.getChangeRatePercent()).isNull();
        assertThat(response.getDetectedAt()).isEqualTo(DETECTED_AT);
    }

    @ParameterizedTest
    @EnumSource(
            value = SignalType.class,
            names = {"VOLUME_SPIKE", "MOVING_AVERAGE_BREAKOUT"})
    void convertsLegacySignalWithNullSearchCondition(SignalType signalType) {
        Stock stock = saveStock("000660", "SK하이닉스");
        insertLegacySignal(stock.getId(), signalType);
        entityManager.clear();

        Signal signal = signalRepository
                .findAllWithStockOrderByDetectedAtDesc()
                .getFirst();
        SignalResponse response = SignalResponse.from(signal);

        assertThat(response.getSignalType()).isEqualTo(signalType);
        assertThat(response.getSearchConditionId()).isNull();
        assertThat(response.getSearchConditionName()).isNull();
        assertThat(response.getBaseValue()).isEqualTo(100L);
        assertThat(response.getCurrentValue()).isEqualTo(300L);
        assertThat(response.getChangeRate()).isEqualTo(3.0);
        assertThat(response.getChangeRatePercent()).isEqualTo(200.0);
    }

    private Stock saveStock(String stockCode, String stockName) {
        return stockRepository.saveAndFlush(Stock.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .marketType(MarketType.KOSPI)
                .build());
    }

    private void insertLegacySignal(Long stockId, SignalType signalType) {
        entityManager.createNativeQuery("""
                        INSERT INTO signals (
                            stock_id, search_condition_id, signal_type, message,
                            base_value, current_value, change_rate, detected_at
                        ) VALUES (?, NULL, ?, ?, 100, 300, 3.0, ?)
                        """)
                .setParameter(1, stockId)
                .setParameter(2, signalType.name())
                .setParameter(3, "legacy signal")
                .setParameter(4, DETECTED_AT)
                .executeUpdate();
    }
}
