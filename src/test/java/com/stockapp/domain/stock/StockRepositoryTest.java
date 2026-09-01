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

    @Test
    void eligibilityQueriesConformToDomainPolicyAndPreserveIdOrder() {
        SupportedInstrumentPolicy supportedPolicy =
                new SupportedInstrumentPolicy();
        OperationalStockEligibilityPolicy eligibilityPolicy =
                new OperationalStockEligibilityPolicy(supportedPolicy);
        stockRepository.saveAll(List.of(
                masterStock("100001", MarketType.KOSPI, true,
                        InstrumentType.COMMON_STOCK, false, false),
                masterStock("100002", MarketType.KOSDAQ, true,
                        InstrumentType.SPAC, true, false),
                masterStock("100003", MarketType.KOSPI, true,
                        InstrumentType.REIT, false, true),
                masterStock("100004", MarketType.KOSPI, false,
                        InstrumentType.COMMON_STOCK, false, false),
                masterStock("100005", MarketType.KOSPI, null,
                        InstrumentType.COMMON_STOCK, false, false),
                masterStock("100006", MarketType.KOSPI, true,
                        InstrumentType.PREFERRED_STOCK, false, false),
                masterStock("100007", MarketType.KOSPI, true,
                        null, false, false),
                masterStock("100008", MarketType.KONEX, true,
                        InstrumentType.COMMON_STOCK, false, false),
                masterStock("100009", MarketType.KOSDAQ, true,
                        InstrumentType.LISTED_FUND, null, false),
                masterStock("100010", MarketType.KOSDAQ, true,
                        InstrumentType.FOREIGN_STOCK, false, null)));
        stockRepository.flush();

        List<Stock> all = stockRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Stock::getId))
                .toList();
        List<Stock> expectedHistory = all.stream()
                .filter(eligibilityPolicy::isHistoryEligible)
                .toList();
        List<Stock> expectedCurrent = all.stream()
                .filter(eligibilityPolicy::isCurrentEligible)
                .toList();

        List<Stock> history = stockRepository.findHistoryEligibleStocks(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ),
                supportedPolicy.supportedTypes());
        List<Stock> current = stockRepository.findCurrentEligibleStocks(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ),
                supportedPolicy.supportedTypes());

        assertThat(history).extracting(Stock::getId)
                .containsExactlyElementsOf(expectedHistory.stream()
                        .map(Stock::getId).toList());
        assertThat(current).extracting(Stock::getId)
                .containsExactlyElementsOf(expectedCurrent.stream()
                        .map(Stock::getId).toList());
    }

    private Stock stock(String code, String name, MarketType marketType) {
        return Stock.builder()
                .stockCode(code)
                .stockName(name)
                .marketType(marketType)
                .build();
    }

    private Stock masterStock(
            String code,
            MarketType marketType,
            Boolean present,
            InstrumentType instrumentType,
            Boolean suspended,
            Boolean liquidationTrading
    ) {
        return Stock.builder()
                .stockCode(code)
                .stockName("Stock " + code)
                .marketType(marketType)
                .presentInLatestMaster(present)
                .instrumentType(instrumentType)
                .suspended(suspended)
                .liquidationTrading(liquidationTrading)
                .build();
    }
}
