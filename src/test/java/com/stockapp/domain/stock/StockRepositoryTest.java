package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StockRepositoryTest {

    @Autowired StockRepository stockRepository;

    @Test
    void bulkLookupIncludesKospiAndKosdaqButExcludesKonexAndUnknownCodes() {
        stockRepository.saveAll(List.of(
                stock("005930", "삼성전자", MarketType.KOSPI),
                stock("035720", "카카오", MarketType.KOSDAQ),
                stock("950000", "코넥스종목", MarketType.KONEX)));

        List<Stock> result = stockRepository.findByStockCodeInAndMarketTypeIn(
                List.of("005930", "035720", "950000", "999999"),
                List.of(MarketType.KOSPI, MarketType.KOSDAQ));

        assertThat(result)
                .extracting(Stock::getStockCode)
                .containsExactlyInAnyOrder("005930", "035720");
    }

    @Test
    void allMarketLookupExcludesKonexAndReturnsIdAscending() {
        Stock first = stockRepository.save(
                stock("005930", "Samsung", MarketType.KOSPI));
        Stock second = stockRepository.save(
                stock("035720", "Kakao", MarketType.KOSDAQ));
        stockRepository.save(stock("950000", "Konex", MarketType.KONEX));

        List<Stock> result = stockRepository.findByMarketTypeInOrderByIdAsc(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ));

        assertThat(result).containsExactly(first, second);
        assertThat(result)
                .extracting(Stock::getMarketType)
                .containsOnly(MarketType.KOSPI, MarketType.KOSDAQ);
    }

    private Stock stock(String code, String name, MarketType marketType) {
        return Stock.builder()
                .stockCode(code)
                .stockName(name)
                .marketType(marketType)
                .build();
    }
}
