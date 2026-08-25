package com.stockapp.domain.screening;

import com.stockapp.domain.screening.metric.OperationalScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.OperationalScreeningDataRequirements;
import com.stockapp.domain.screening.metric.OperationalDailyHistoryRequirementAnalyzer;
import com.stockapp.domain.screening.metric.OperationalStockMetricContextFactory;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationalScreeningRunService {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final OperationalScreeningEvaluationDateResolver readinessResolver;
    private final OperationalScreeningCompletenessService completenessService;
    private final OperationalDailyHistoryRequirementAnalyzer
            dailyHistoryRequirementAnalyzer;
    private final DailyHistoryBootstrapReadinessService
            bootstrapReadinessService;
    private final SearchConditionRepository searchConditionRepository;
    private final StockRepository stockRepository;
    private final OperationalScreeningDataRequirementAnalyzer requirementAnalyzer;
    private final OperationalStockMetricContextFactory contextFactory;
    private final ScreeningEvaluationEngine evaluationEngine;

    public OperationalScreeningRunResult run() {
        OperationalScreeningReadinessResult readiness =
                readinessResolver.resolve();
        if (readiness.status()
                == OperationalScreeningReadinessStatus.NOT_TRADING_DAY) {
            return OperationalScreeningRunResult.notTradingDay(
                    readiness.today());
        }

        LocalDate evaluationDate = readiness.expectedEvaluationDate()
                .orElseThrow();
        if (readiness.status()
                == OperationalScreeningReadinessStatus.FINALIZATION_NOT_READY) {
            return OperationalScreeningRunResult.finalizationNotReady(
                    readiness.today(), evaluationDate);
        }

        List<SearchCondition> conditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        int requiredPreviousTradingDayCount = dailyHistoryRequirementAnalyzer
                .analyze(conditions)
                .requiredPreviousTradingDayCount();
        DailyHistoryBootstrapReadinessResult bootstrapReadiness =
                bootstrapReadinessService.check(
                        evaluationDate, requiredPreviousTradingDayCount);
        if (!bootstrapReadiness.ready()) {
            return OperationalScreeningRunResult.historyBootstrapNotReady(
                    readiness.today(), evaluationDate);
        }

        OperationalScreeningCompletenessResult completeness =
                completenessService.check(evaluationDate);
        if (completeness.status()
                == OperationalScreeningCompletenessStatus.INCOMPLETE) {
            return OperationalScreeningRunResult.dataIncomplete(
                    readiness.today(), evaluationDate, completeness);
        }

        List<Stock> stocks = stockRepository
                .findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        if (stocks.isEmpty()) {
            throw new IllegalStateException(
                    "operational screening target universe became empty");
        }

        if (conditions.isEmpty()) {
            var screeningResult = evaluationEngine
                    .evaluateWithoutConditions(stocks, evaluationDate);
            return OperationalScreeningRunResult.completed(
                    readiness.today(), evaluationDate,
                    completeness, screeningResult);
        }
        OperationalScreeningDataRequirements requirements =
                requirementAnalyzer.analyze(conditions);
        return completed(readiness, evaluationDate, completeness,
                stocks, conditions, requirements);
    }

    private OperationalScreeningRunResult completed(
            OperationalScreeningReadinessResult readiness,
            LocalDate evaluationDate,
            OperationalScreeningCompletenessResult completeness,
            List<Stock> stocks,
            List<SearchCondition> conditions,
            OperationalScreeningDataRequirements requirements
    ) {
        var screeningResult = evaluationEngine.evaluate(
                stocks, evaluationDate, conditions,
                stock -> contextFactory.createWithRequirements(
                        stock, requirements, evaluationDate));
        return OperationalScreeningRunResult.completed(
                readiness.today(), evaluationDate,
                completeness, screeningResult);
    }
}
