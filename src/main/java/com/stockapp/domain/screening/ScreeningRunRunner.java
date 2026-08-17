package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningFailure;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Profile("screening-run")
public class ScreeningRunRunner implements ApplicationRunner {

    static final int MAX_STOCK_COUNT = 50;
    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final ScreeningRunService screeningRunService;
    private final StockRepository stockRepository;
    private final String stockCodes;
    private final boolean allStocks;
    private final String baseDate;

    public ScreeningRunRunner(
            ScreeningRunService screeningRunService,
            StockRepository stockRepository,
            @Value("${screening-run.stock-codes:}") String stockCodes,
            @Value("${screening-run.all-stocks:false}") boolean allStocks,
            @Value("${screening-run.base-date:}") String baseDate
    ) {
        this.screeningRunService = screeningRunService;
        this.stockRepository = stockRepository;
        this.stockCodes = stockCodes;
        this.allStocks = allStocks;
        this.baseDate = baseDate;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate parsedBaseDate = parseBaseDate(baseDate);
        List<Stock> stocks = allStocks
                ? findAllMarketStocks()
                : findLimitedStocks();

        if (allStocks) {
            log.warn("screening-run all-market start - stockCount: {}, baseDate: {}",
                    stocks.size(), parsedBaseDate);
        }
        log.info("screening-run 시작 - stockCount: {}, baseDate: {}",
                stocks.size(), parsedBaseDate);
        ScreeningRunResult result = screeningRunService.run(stocks, parsedBaseDate);
        logResult(result, allStocks);
    }

    private List<Stock> findLimitedStocks() {
        List<String> requestedStockCodes = parseStockCodes(stockCodes);
        return findStocksInInputOrder(requestedStockCodes);
    }

    private List<Stock> findAllMarketStocks() {
        if (stockCodes != null && !stockCodes.isBlank()) {
            parseStockCodes(stockCodes);
            throw new IllegalArgumentException(
                    "screening-run.stock-codes and all-stocks=true are mutually exclusive");
        }
        List<Stock> stocks = stockRepository
                .findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        if (stocks.isEmpty()) {
            throw new IllegalStateException(
                    "no KOSPI/KOSDAQ stocks found for all-stocks screening");
        }
        return stocks;
    }

    private List<String> parseStockCodes(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "screening-run.stock-codes is required");
        }

        String[] tokens = value.split(",", -1);
        List<String> parsed = java.util.Arrays.stream(tokens)
                .map(String::trim)
                .toList();
        if (parsed.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "screening-run.stock-codes must not contain empty tokens");
        }
        if (parsed.size() > MAX_STOCK_COUNT) {
            throw new IllegalArgumentException(
                    "screening-run.stock-codes must contain at most "
                            + MAX_STOCK_COUNT + " stocks");
        }

        Set<String> uniqueCodes = new LinkedHashSet<>(parsed);
        if (uniqueCodes.size() != parsed.size()) {
            throw new IllegalArgumentException(
                    "screening-run.stock-codes must not contain duplicates");
        }
        return parsed;
    }

    private LocalDate parseBaseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "screening-run.base-date is required");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "screening-run.base-date must use YYYY-MM-DD format",
                    exception);
        }
    }

    private List<Stock> findStocksInInputOrder(List<String> requestedStockCodes) {
        List<Stock> foundStocks = stockRepository
                .findByStockCodeInAndMarketTypeIn(
                        requestedStockCodes, TARGET_MARKETS);
        Map<String, Stock> stocksByCode = new HashMap<>();
        for (Stock stock : foundStocks) {
            stocksByCode.put(stock.getStockCode(), stock);
        }

        List<String> invalidCodes = requestedStockCodes.stream()
                .filter(code -> !stocksByCode.containsKey(code))
                .toList();
        if (!invalidCodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "stocks not found or not in KOSPI/KOSDAQ: " + invalidCodes);
        }
        return requestedStockCodes.stream()
                .map(stocksByCode::get)
                .toList();
    }

    private void logResult(ScreeningRunResult result, boolean allMarket) {
        long elapsedMs = Duration.between(
                result.startedAt(), result.finishedAt()).toMillis();
        log.info("screening-run 완료 - baseDate: {}, inputStockCount: {}, "
                        + "evaluatedStockCount: {}, candidateStockCount: {}, "
                        + "totalMatchCount: {}, failedStockCount: {}, elapsedMs: {}",
                result.baseDate(), result.totalStockCount(),
                result.evaluatedStockCount(), result.candidateStockCount(),
                result.totalMatchCount(), result.failedStockCount(), elapsedMs);

        for (ScreeningCandidate candidate : result.candidates()) {
            Stock stock = candidate.stock();
            if (allMarket) {
                long realtimeMatchCount = candidate.matches().stream()
                        .filter(ScreeningMatch::realtimeEnabled)
                        .count();
                log.info("screening candidate - stockCode: {}, matchCount: {}, "
                                + "realtimeMatchCount: {}",
                        stock.getStockCode(), candidate.matches().size(),
                        realtimeMatchCount);
                continue;
            }
            log.info("screening candidate - stockCode: {}, stockName: {}",
                    stock.getStockCode(), stock.getStockName());
            for (ScreeningMatch match : candidate.matches()) {
                log.info("screening match - conditionId: {}, conditionName: {}, "
                                + "screeningScore: {}, priority: {}, realtimeEnabled: {}",
                        match.condition().getId(), match.condition().getName(),
                        match.screeningScore(), match.priority(),
                        match.realtimeEnabled());
            }
        }
        for (ScreeningFailure failure : result.failures()) {
            log.warn("screening failure - stockCode: {}, stockName: {}, "
                            + "reason: {}, message: {}",
                    failure.stockCode(), failure.stockName(),
                    failure.reason(), failure.message());
        }
    }
}
