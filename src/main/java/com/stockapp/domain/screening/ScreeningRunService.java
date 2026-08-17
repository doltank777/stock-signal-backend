package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningFailure;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.screening.metric.ScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.ScreeningDataRequirements;
import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScreeningRunService {

    private static final int PROGRESS_LOG_INTERVAL = 100;
    private static final int PROGRESS_LOG_MIN_TOTAL = 51;

    private static final String STOCK_DATA_FAILURE_REASON =
            "STOCK_MARKET_DATA_INVALID";
    private static final String STOCK_DATA_FAILURE_MESSAGE =
            "종목 Screening 시장 데이터가 유효하지 않습니다.";

    private final SearchConditionRepository searchConditionRepository;
    private final ScreeningDataRequirementAnalyzer requirementAnalyzer;
    private final StockMetricContextFactory stockMetricContextFactory;
    private final ScreeningExecutionService screeningExecutionService;
    private final Clock clock;

    public ScreeningRunResult run(
            List<Stock> stocks,
            LocalDate baseDate
    ) {
        validateInputs(stocks, baseDate);

        Instant startedAt = Instant.now(clock);
        if (stocks.isEmpty()) {
            return completedResult(
                    baseDate, startedAt,
                    0, 0, List.of(), List.of());
        }

        List<SearchCondition> executableConditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        if (executableConditions.isEmpty()) {
            return completedResult(
                    baseDate, startedAt,
                    stocks.size(), stocks.size(),
                    List.of(), List.of());
        }

        ScreeningDataRequirements requirements = requirementAnalyzer
                .analyze(executableConditions);
        List<ScreeningCandidate> candidates = new ArrayList<>();
        List<ScreeningFailure> failures = new ArrayList<>();
        int evaluatedStockCount = 0;

        for (int index = 0; index < stocks.size(); index++) {
            Stock stock = stocks.get(index);
            try {
                evaluateStock(
                        stock,
                        baseDate,
                        executableConditions,
                        requirements).ifPresent(candidates::add);
                evaluatedStockCount++;
            } catch (ScreeningStockDataException exception) {
                failures.add(toFailure(stock, exception));
            }
            logProgressIfNeeded(
                    index + 1, stocks.size(), candidates.size(),
                    failures.size(), startedAt);
        }

        return completedResult(
                baseDate, startedAt,
                stocks.size(), evaluatedStockCount,
                candidates, failures);
    }

    private void logProgressIfNeeded(
            int processedStockCount,
            int totalStockCount,
            int candidateStockCount,
            int failedStockCount,
            Instant startedAt
    ) {
        if (totalStockCount < PROGRESS_LOG_MIN_TOTAL) {
            return;
        }
        if (processedStockCount % PROGRESS_LOG_INTERVAL != 0
                && processedStockCount != totalStockCount) {
            return;
        }
        long elapsedMs = Duration.between(
                startedAt, Instant.now(clock)).toMillis();
        log.info("screening progress - processed={}/{}, candidates={}, "
                        + "failures={}, elapsedMs={}",
                processedStockCount, totalStockCount, candidateStockCount,
                failedStockCount, elapsedMs);
    }

    private Optional<ScreeningCandidate> evaluateStock(
            Stock stock,
            LocalDate baseDate,
            List<SearchCondition> conditions,
            ScreeningDataRequirements requirements
    ) {
        StockMetricContext context = stockMetricContextFactory
                .createWithRequirements(stock, requirements, baseDate);
        List<ScreeningMatch> matches = evaluateConditions(
                conditions, context);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ScreeningCandidate(
                stock, baseDate, matches));
    }

    private List<ScreeningMatch> evaluateConditions(
            List<SearchCondition> conditions,
            StockMetricContext context
    ) {
        List<ScreeningMatch> matches = new ArrayList<>();
        for (SearchCondition condition : conditions) {
            if (screeningExecutionService.evaluate(condition, context)) {
                matches.add(new ScreeningMatch(
                        condition,
                        condition.getScreeningScore(),
                        condition.getPriority(),
                        condition.isRealtimeEnabled()));
            }
        }
        return matches;
    }

    private ScreeningRunResult completedResult(
            LocalDate baseDate,
            Instant startedAt,
            int totalStockCount,
            int evaluatedStockCount,
            List<ScreeningCandidate> candidates,
            List<ScreeningFailure> failures
    ) {
        return new ScreeningRunResult(
                baseDate,
                startedAt,
                Instant.now(clock),
                totalStockCount,
                evaluatedStockCount,
                candidates,
                failures);
    }

    private ScreeningFailure toFailure(
            Stock stock,
            ScreeningStockDataException exception
    ) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = STOCK_DATA_FAILURE_MESSAGE;
        }
        return new ScreeningFailure(
                stock.getStockCode(),
                stock.getStockName(),
                STOCK_DATA_FAILURE_REASON,
                message);
    }

    private void validateInputs(
            List<Stock> stocks,
            LocalDate baseDate
    ) {
        if (stocks == null) {
            throw new IllegalArgumentException("stocks are required");
        }
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }

        Set<String> stockCodes = new HashSet<>();
        for (Stock stock : stocks) {
            if (stock == null) {
                throw new IllegalArgumentException(
                        "stocks must not contain null");
            }
            if (!stockCodes.add(stock.getStockCode())) {
                throw new IllegalArgumentException(
                        "duplicate stockCode: " + stock.getStockCode());
            }
        }
    }
}
