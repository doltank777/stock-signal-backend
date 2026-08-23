package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OperationalStockMetricContextFactory {

    private final OperationalScreeningMarketDataQueryService queryService;
    private final OperationalScreeningDataRequirementAnalyzer requirementAnalyzer;

    public StockMetricContext create(
            Stock stock,
            List<SearchCondition> conditions,
            LocalDate evaluationDate
    ) {
        return createWithRequirements(
                stock,
                requirementAnalyzer.analyze(conditions),
                evaluationDate);
    }

    public StockMetricContext createWithRequirements(
            Stock stock,
            OperationalScreeningDataRequirements requirements,
            LocalDate evaluationDate
    ) {
        OperationalScreeningMarketData data = queryService.load(
                stock, evaluationDate, requirements);
        return new StockMetricContext(
                stock,
                evaluationDate,
                Optional.empty(),
                Optional.of(data.current()),
                data.history());
    }
}
