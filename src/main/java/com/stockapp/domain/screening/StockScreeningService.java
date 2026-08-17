package com.stockapp.domain.screening;

import com.stockapp.domain.screening.metric.ScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.ScreeningDataRequirements;
import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockScreeningService {

    private final SearchConditionRepository searchConditionRepository;
    private final ScreeningDataRequirementAnalyzer requirementAnalyzer;
    private final StockMetricContextFactory stockMetricContextFactory;
    private final ScreeningExecutionService screeningExecutionService;

    public List<SearchCondition> screen(
            Stock stock,
            LocalDate baseDate
    ) {
        validateInputs(stock, baseDate);

        List<SearchCondition> executableConditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        if (executableConditions.isEmpty()) {
            return List.of();
        }

        ScreeningDataRequirements requirements = requirementAnalyzer
                .analyze(executableConditions);
        StockMetricContext context = stockMetricContextFactory
                .createWithRequirements(stock, requirements, baseDate);
        List<SearchCondition> matchedConditions = new ArrayList<>();

        for (SearchCondition condition : executableConditions) {
            if (screeningExecutionService.evaluate(condition, context)) {
                matchedConditions.add(condition);
            }
        }

        return List.copyOf(matchedConditions);
    }

    private void validateInputs(
            Stock stock,
            LocalDate baseDate
    ) {
        if (stock == null) {
            throw new IllegalArgumentException("stock is required");
        }
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }
    }
}
