package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.screening.metric.ScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.ScreeningDataRequirements;
import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScreeningRunService {

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
                    baseDate, startedAt, 0, 0, List.of());
        }

        List<SearchCondition> executableConditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        if (executableConditions.isEmpty()) {
            return completedResult(
                    baseDate, startedAt,
                    stocks.size(), stocks.size(), List.of());
        }

        ScreeningDataRequirements requirements = requirementAnalyzer
                .analyze(executableConditions);
        List<ScreeningCandidate> candidates = new ArrayList<>();
        int evaluatedStockCount = 0;

        for (Stock stock : stocks) {
            StockMetricContext context = stockMetricContextFactory
                    .createWithRequirements(stock, requirements, baseDate);
            List<ScreeningMatch> matches = evaluateConditions(
                    executableConditions, context);

            if (!matches.isEmpty()) {
                candidates.add(new ScreeningCandidate(
                        stock, baseDate, matches));
            }
            evaluatedStockCount++;
        }

        return completedResult(
                baseDate, startedAt,
                stocks.size(), evaluatedStockCount, candidates);
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
            List<ScreeningCandidate> candidates
    ) {
        return new ScreeningRunResult(
                baseDate,
                startedAt,
                Instant.now(clock),
                totalStockCount,
                evaluatedStockCount,
                candidates,
                List.of());
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
