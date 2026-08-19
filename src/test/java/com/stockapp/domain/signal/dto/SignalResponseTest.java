package com.stockapp.domain.signal.dto;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.signal.Signal;
import com.stockapp.domain.signal.SignalRepository;
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
    void convertsSearchConditionSignal() {
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
        assertThat(response.getMessage()).isEqualTo("검색식 SIGNAL 조건 일치");
        assertThat(response.getDetectedAt()).isEqualTo(DETECTED_AT);
    }

    private Stock saveStock(String stockCode, String stockName) {
        return stockRepository.saveAndFlush(Stock.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .marketType(MarketType.KOSPI)
                .build());
    }

}
