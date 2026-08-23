package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningFailure;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScreeningEvaluationEngine {

    private static final int PROGRESS_LOG_INTERVAL = 100;
    private static final int PROGRESS_LOG_MIN_TOTAL = 51;
    private static final String STOCK_DATA_FAILURE_REASON =
            "STOCK_MARKET_DATA_INVALID";
    private static final String STOCK_DATA_FAILURE_MESSAGE =
            "종목 Screening 시장 데이터가 유효하지 않습니다.";

    private final ScreeningExecutionService screeningExecutionService;
    private final Clock clock;

    public ScreeningRunResult evaluateWithoutConditions(
            List<Stock> stocks, LocalDate baseDate) {
        Instant startedAt = Instant.now(clock);
        return completedResult(
                baseDate, startedAt, stocks.size(), stocks.size(),
                List.of(), List.of());
    }

    public ScreeningRunResult evaluate(
            List<Stock> stocks,
            LocalDate baseDate,
            List<SearchCondition> conditions,
            Function<Stock, StockMetricContext> contextProvider
    ) {
        Instant startedAt = Instant.now(clock);
        List<ScreeningCandidate> candidates = new ArrayList<>();
        List<ScreeningFailure> failures = new ArrayList<>();
        int evaluatedStockCount = 0;
        for (int index = 0; index < stocks.size(); index++) {
            Stock stock = stocks.get(index);
            try {
                evaluateStock(stock, baseDate, conditions, contextProvider)
                        .ifPresent(candidates::add);
                evaluatedStockCount++;
            } catch (ScreeningStockDataException exception) {
                failures.add(toFailure(stock, exception));
            }
            logProgressIfNeeded(
                    index + 1, stocks.size(), candidates.size(),
                    failures.size(), startedAt);
        }
        return completedResult(
                baseDate, startedAt, stocks.size(), evaluatedStockCount,
                candidates, failures);
    }

    private Optional<ScreeningCandidate> evaluateStock(
            Stock stock,
            LocalDate baseDate,
            List<SearchCondition> conditions,
            Function<Stock, StockMetricContext> contextProvider
    ) {
        StockMetricContext context = contextProvider.apply(stock);
        List<ScreeningMatch> matches = new ArrayList<>();
        for (SearchCondition condition : conditions) {
            if (screeningExecutionService.evaluate(condition, context)) {
                matches.add(new ScreeningMatch(
                        condition, condition.getScreeningScore(),
                        condition.getPriority(), condition.isRealtimeEnabled()));
            }
        }
        return matches.isEmpty()
                ? Optional.empty()
                : Optional.of(new ScreeningCandidate(stock, baseDate, matches));
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
                baseDate, startedAt, Instant.now(clock),
                totalStockCount, evaluatedStockCount, candidates, failures);
    }

    private ScreeningFailure toFailure(
            Stock stock, ScreeningStockDataException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = STOCK_DATA_FAILURE_MESSAGE;
        }
        return new ScreeningFailure(
                stock.getStockCode(), stock.getStockName(),
                STOCK_DATA_FAILURE_REASON, message);
    }

    private void logProgressIfNeeded(
            int processedStockCount,
            int totalStockCount,
            int candidateStockCount,
            int failedStockCount,
            Instant startedAt
    ) {
        if (totalStockCount < PROGRESS_LOG_MIN_TOTAL
                || processedStockCount % PROGRESS_LOG_INTERVAL != 0
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
}
