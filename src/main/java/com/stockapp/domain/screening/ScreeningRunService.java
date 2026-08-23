package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.screening.metric.ScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.ScreeningDataRequirements;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScreeningRunService {

    private final SearchConditionRepository searchConditionRepository;
    private final ScreeningDataRequirementAnalyzer requirementAnalyzer;
    private final StockMetricContextFactory stockMetricContextFactory;
    private final ScreeningEvaluationEngine evaluationEngine;

    public ScreeningRunResult run(
            List<Stock> stocks,
            LocalDate baseDate
    ) {
        validateInputs(stocks, baseDate);

        if (stocks.isEmpty()) {
            return evaluationEngine.evaluateWithoutConditions(stocks, baseDate);
        }

        List<SearchCondition> executableConditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        if (executableConditions.isEmpty()) {
            return evaluationEngine.evaluateWithoutConditions(stocks, baseDate);
        }

        ScreeningDataRequirements requirements = requirementAnalyzer
                .analyze(executableConditions);
        return evaluationEngine.evaluate(
                stocks, baseDate, executableConditions,
                stock -> stockMetricContextFactory.createWithRequirements(
                        stock, requirements, baseDate));
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
