package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryPlan;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryRequest;
import com.stockapp.domain.stock.dto.BootstrapDailyHistoryStockSummary;
import com.stockapp.domain.stock.dto.DailyHistoryBootstrapExecutionSnapshot;
import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillFailure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
@Profile("daily-history-bootstrap")
@RequiredArgsConstructor
public class DailyHistoryBootstrapRunner implements ApplicationRunner {

    private final BootstrapDailyHistoryBatchService batchService;
    private final DailyHistoryBootstrapExecutionStore executionStore;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        execute();
    }

    BootstrapDailyHistoryBatchResult execute() {
        BootstrapDailyHistoryPlan plan = batchService.resolvePlan();
        BootstrapDailyHistoryRequest request = plan.request();
        DailyHistoryBootstrapExecutionSnapshot execution =
                executionStore.start(
                        request.evaluationDate(),
                        request.requiredPreviousTradingDayCount(),
                        plan.universeFingerprint(),
                        plan.universePolicyVersion(),
                        clock.instant());
        BootstrapDailyHistoryBatchResult result;
        try {
            result = batchService.bootstrap(plan);
            executionStore.complete(execution.id(), result, clock.instant());
        } catch (RuntimeException | Error error) {
            persistFailure(execution.id(), error);
            throw error;
        }
        logSummary(result);
        result.problemStocks().forEach(this::logProblemStock);
        if (!result.ready()) {
            throw new DailyHistoryBootstrapNotReadyException(result);
        }
        return result;
    }

    private void persistFailure(Long executionId, Throwable original) {
        try {
            executionStore.fail(executionId, original, clock.instant());
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
    }

    private void logSummary(BootstrapDailyHistoryBatchResult result) {
        log.info("[DAILY HISTORY BOOTSTRAP]"
                        + "\n\nevaluationDate={}"
                        + "\nrequiredPreviousTradingDayCount={}"
                        + "\nrequiredTradingDateCount={}"
                        + "\ntargetStockCount={}"
                        + "\ncompletedStockCount={}"
                        + "\npartialStockCount={}"
                        + "\nfailedStockCount={}"
                        + "\ntotalInitialMissingCount={}"
                        + "\ntotalRemainingMissingCount={}"
                        + "\nplannedRangeCount={}"
                        + "\nplannedChunkCount={}"
                        + "\nattemptedChunkCount={}"
                        + "\napiCallCount={}"
                        + "\nsavedRowCount={}"
                        + "\nskippedRowCount={}"
                        + "\nemptyResponseChunkCount={}"
                        + "\noutOfRangeResponseRowCount={}"
                        + "\nstatus={}"
                        + "\nready={}",
                result.evaluationDate(),
                result.requiredPreviousTradingDayCount(),
                result.requiredTradingDateCount(),
                result.targetStockCount(),
                result.completedStockCount(),
                result.partialStockCount(),
                result.failedStockCount(),
                result.totalInitialMissingCount(),
                result.totalRemainingMissingCount(),
                result.plannedRangeCount(),
                result.plannedChunkCount(),
                result.attemptedChunkCount(),
                result.apiCallCount(),
                result.savedRowCount(),
                result.skippedRowCount(),
                result.emptyResponseChunkCount(),
                result.outOfRangeResponseRowCount(),
                result.status(),
                result.ready());
    }

    private void logProblemStock(BootstrapDailyHistoryStockSummary summary) {
        BootstrapMissingHistoryFetchFillFailure failure = summary.failure();
        if (failure == null) {
            log.warn("Daily history bootstrap problem stock - stockCode: {}, "
                            + "status: {}, remainingMissingCount: {}",
                    summary.stockCode(), summary.status(),
                    summary.remainingMissingCount());
            return;
        }
        log.warn("Daily history bootstrap problem stock - stockCode: {}, "
                        + "status: {}, remainingMissingCount: {}, "
                        + "chunkStartDate: {}, chunkEndDate: {}, "
                        + "exceptionType: {}, message: {}, attemptCount: {}",
                summary.stockCode(), summary.status(),
                summary.remainingMissingCount(),
                failure.chunk().startDate(), failure.chunk().endDate(),
                failure.exceptionType(), failure.message(),
                failure.attemptCount());
    }
}
