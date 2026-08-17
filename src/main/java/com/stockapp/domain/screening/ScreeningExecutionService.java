package com.stockapp.domain.screening;

import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.screening.rule.ScreeningConditionEvaluator;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ScreeningExecutionService {

    private final StockMetricContextFactory stockMetricContextFactory;
    private final ScreeningConditionEvaluator screeningConditionEvaluator;

    public boolean evaluate(
            Stock stock,
            SearchCondition condition,
            LocalDate baseDate
    ) {
        validateInputs(stock, condition, baseDate);

        StockMetricContext context = stockMetricContextFactory.create(
                stock, condition, baseDate);
        return evaluate(condition, context);
    }

    public boolean evaluate(
            SearchCondition condition,
            StockMetricContext context
    ) {
        if (condition == null) {
            throw new IllegalArgumentException("condition is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        return screeningConditionEvaluator.evaluate(condition, context);
    }

    private void validateInputs(
            Stock stock,
            SearchCondition condition,
            LocalDate baseDate
    ) {
        if (stock == null) {
            throw new IllegalArgumentException("stock is required");
        }
        if (condition == null) {
            throw new IllegalArgumentException("condition is required");
        }
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }
    }
}
