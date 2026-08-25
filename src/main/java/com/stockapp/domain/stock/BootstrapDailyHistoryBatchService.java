package com.stockapp.domain.stock;

import com.stockapp.domain.screening.OperationalScreeningEvaluationDateResolver;
import com.stockapp.domain.screening.OperationalScreeningReadinessResult;
import com.stockapp.domain.screening.OperationalScreeningReadinessStatus;
import com.stockapp.domain.screening.metric.OperationalDailyHistoryRequirement;
import com.stockapp.domain.screening.metric.OperationalDailyHistoryRequirementAnalyzer;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryStockSummary;
import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BootstrapDailyHistoryBatchService {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final OperationalScreeningEvaluationDateResolver evaluationDateResolver;
    private final OperationalDailyHistoryRequirementAnalyzer requirementAnalyzer;
    private final KrxTradingCalendar tradingCalendar;
    private final StockRepository stockRepository;
    private final BootstrapMissingHistoryFetchFillService fetchFillService;

    public BootstrapDailyHistoryBatchResult bootstrap() {
        LocalDate evaluationDate = resolveEvaluationDate();
        OperationalDailyHistoryRequirement requirement =
                requirementAnalyzer.analyze();
        int requiredPreviousCount =
                requirement.requiredPreviousTradingDayCount();
        List<Stock> stocks = stockRepository.findByMarketTypeInOrderByIdAsc(
                TARGET_MARKETS);
        if (stocks.isEmpty()) {
            throw new IllegalStateException(
                    "bootstrap target universe became empty");
        }

        if (requiredPreviousCount == 0) {
            return Summary.withNoRequiredHistory(
                    evaluationDate, stocks.size()).toResult();
        }

        List<LocalDate> requiredTradingDates = tradingCalendar
                .previousTradingDays(evaluationDate, requiredPreviousCount);
        Summary summary = new Summary(
                evaluationDate,
                requiredPreviousCount,
                requiredTradingDates.size(),
                stocks.size());
        log.info("Daily history bootstrap batch started - evaluationDate: {}, "
                        + "requiredPreviousTradingDays: {}, targetStocks: {}",
                evaluationDate, requiredPreviousCount, stocks.size());

        for (Stock stock : stocks) {
            BootstrapMissingHistoryFetchFillResult stockResult =
                    fetchFillService.fetchAndFill(stock, requiredTradingDates);
            summary.add(stockResult);
        }

        BootstrapDailyHistoryBatchResult result = summary.toResult();
        log.info("Daily history bootstrap batch finished - evaluationDate: {}, "
                        + "completed: {}, partial: {}, failed: {}, "
                        + "remainingMissing: {}, ready: {}",
                evaluationDate, result.completedStockCount(),
                result.partialStockCount(), result.failedStockCount(),
                result.totalRemainingMissingCount(), result.ready());
        return result;
    }

    private LocalDate resolveEvaluationDate() {
        OperationalScreeningReadinessResult readiness =
                evaluationDateResolver.resolve();
        if (readiness.status()
                == OperationalScreeningReadinessStatus.NOT_TRADING_DAY) {
            throw new IllegalStateException(
                    "bootstrap evaluation date is unavailable on a non-trading day");
        }
        return readiness.expectedEvaluationDate().orElseThrow(() ->
                new IllegalStateException(
                        "bootstrap evaluation date is unavailable"));
    }

    private static class Summary {
        private final LocalDate evaluationDate;
        private final int requiredPreviousTradingDayCount;
        private final int requiredTradingDateCount;
        private final int targetStockCount;
        private final List<BootstrapDailyHistoryStockSummary> problemStocks =
                new ArrayList<>();
        private int completedStockCount;
        private int partialStockCount;
        private int failedStockCount;
        private int totalInitialMissingCount;
        private int totalRemainingMissingCount;
        private int plannedRangeCount;
        private int plannedChunkCount;
        private int attemptedChunkCount;
        private int apiCallCount;
        private int savedRowCount;
        private int skippedRowCount;
        private int emptyResponseChunkCount;
        private int outOfRangeResponseRowCount;

        private Summary(
                LocalDate evaluationDate,
                int requiredPreviousTradingDayCount,
                int requiredTradingDateCount,
                int targetStockCount
        ) {
            this.evaluationDate = evaluationDate;
            this.requiredPreviousTradingDayCount =
                    requiredPreviousTradingDayCount;
            this.requiredTradingDateCount = requiredTradingDateCount;
            this.targetStockCount = targetStockCount;
        }

        private static Summary withNoRequiredHistory(
                LocalDate evaluationDate,
                int targetStockCount
        ) {
            Summary summary = new Summary(
                    evaluationDate, 0, 0, targetStockCount);
            summary.completedStockCount = targetStockCount;
            return summary;
        }

        private void add(BootstrapMissingHistoryFetchFillResult result) {
            switch (result.status()) {
                case COMPLETED -> completedStockCount++;
                case PARTIAL -> partialStockCount++;
                case FAILED -> failedStockCount++;
            }
            totalInitialMissingCount += result.initialMissingCount();
            totalRemainingMissingCount += result.remainingMissingDates().size();
            plannedRangeCount += result.plannedRangeCount();
            plannedChunkCount += result.plannedChunkCount();
            attemptedChunkCount += result.attemptedChunkCount();
            apiCallCount += result.apiCallCount();
            savedRowCount += result.savedRowCount();
            skippedRowCount += result.skippedRowCount();
            emptyResponseChunkCount += result.emptyResponseChunkCount();
            outOfRangeResponseRowCount +=
                    result.outOfRangeResponseRowCount();
            if (result.status()
                    != BootstrapMissingHistoryFetchFillStatus.COMPLETED) {
                problemStocks.add(new BootstrapDailyHistoryStockSummary(
                        result.stockCode(),
                        result.status(),
                        result.remainingMissingDates().size(),
                        result.failure()));
            }
        }

        private BootstrapDailyHistoryBatchResult toResult() {
            BootstrapDailyHistoryBatchStatus status =
                    partialStockCount == 0
                            && failedStockCount == 0
                            && totalRemainingMissingCount == 0
                            ? BootstrapDailyHistoryBatchStatus.COMPLETED
                            : BootstrapDailyHistoryBatchStatus.COMPLETED_WITH_GAPS;
            return new BootstrapDailyHistoryBatchResult(
                    status,
                    evaluationDate,
                    requiredPreviousTradingDayCount,
                    requiredTradingDateCount,
                    targetStockCount,
                    completedStockCount,
                    partialStockCount,
                    failedStockCount,
                    totalInitialMissingCount,
                    totalRemainingMissingCount,
                    plannedRangeCount,
                    plannedChunkCount,
                    attemptedChunkCount,
                    apiCallCount,
                    savedRowCount,
                    skippedRowCount,
                    emptyResponseChunkCount,
                    outOfRangeResponseRowCount,
                    problemStocks);
        }
    }
}
