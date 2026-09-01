package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceFinalizationBatchResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationExecutionResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationTarget;
import com.stockapp.domain.stock.dto.DailyPriceLoadFailure;
import com.stockapp.external.kis.KisApiException;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceFinalizationBatchService {

    private static final String RATE_LIMIT_CODE = "EGW00201";
    private final OperationalStockUniverseService stockUniverseService;
    private final DailyPriceFinalizationService finalizationService;
    private final DailyPriceCompletenessEvaluator completenessEvaluator;
    private final KisProperties kisProperties;
    private final DailyPriceLoadSleeper sleeper;
    private final Clock clock;
    private final DailyPriceFinalizationRunGuard runGuard;

    public DailyPriceFinalizationExecutionResult finalizeAll(
            LocalDate targetTradeDate
    ) {
        runGuard.acquire();
        try {
            return finalizeAllWithinGuard(targetTradeDate);
        } finally {
            runGuard.release();
        }
    }

    DailyPriceFinalizationExecutionResult finalizeAllWithinGuard(
            LocalDate targetTradeDate
    ) {
        if (targetTradeDate == null) {
            throw new IllegalArgumentException("targetTradeDate is required");
        }
        validateConfiguration();

        List<Stock> stocks = stockUniverseService.findHistoryTargets();
        BatchSummary summary = new BatchSummary(
                targetTradeDate, stocks, Instant.now(clock));
        log.info("일봉 Finalization batch 시작 - targetTradeDate: {}, target: {}",
                targetTradeDate, stocks.size());

        for (Stock stock : stocks) {
            try {
                DailyPriceFinalizationResult result = finalizeWithRetry(
                        stock, targetTradeDate, summary);
                summary.add(result);
            } catch (DailyPriceFinalizationInterruptedException exception) {
                log.warn("일봉 Finalization batch가 인터럽트되어 중단됩니다.");
                throw exception;
            } catch (RuntimeException exception) {
                if (isAuthenticationFailure(exception)) {
                    log.error("KIS 인증 오류로 일봉 Finalization batch를 중단합니다. status: {}",
                            ((RestClientResponseException) exception)
                                    .getStatusCode().value());
                    throw exception;
                }
                summary.addFailure(stock, failureReason(exception),
                        findKisMessageCode(exception));
                log.error("일봉 Finalization 종목 실패 - stockCode: {}, error: {}",
                        stock.getStockCode(), exception.getMessage());
            }
        }

        List<Stock> secondPassTargets = stocks.stream()
                .filter(stock -> summary.isFailed(stock.getStockCode()))
                .toList();
        for (Stock stock : secondPassTargets) {
            try {
                DailyPriceFinalizationResult result = finalizeWithRetry(
                        stock, targetTradeDate, summary);
                summary.add(result);
            } catch (DailyPriceFinalizationInterruptedException exception) {
                log.warn("일봉 Finalization second pass가 인터럽트되어 중단됩니다.");
                throw exception;
            } catch (RuntimeException exception) {
                if (isAuthenticationFailure(exception)) {
                    throw exception;
                }
                summary.addFailure(stock, failureReason(exception),
                        findKisMessageCode(exception));
            }
        }

        DailyPriceFinalizationBatchResult batch = summary.toResult(
                Instant.now(clock));
        log.info("일봉 Finalization batch 완료 - targetTradeDate: {}, target: {}, "
                        + "inserted: {}, updated: {}, unchanged: {}, noData: {}, failed: {}",
                targetTradeDate, batch.targetStockCount(),
                batch.insertedStockCount(), batch.updatedStockCount(),
                batch.unchangedStockCount(), batch.noDataStockCount(),
                batch.failedStockCount());
        return new DailyPriceFinalizationExecutionResult(
                batch, completenessEvaluator.evaluate(batch));
    }

    private DailyPriceFinalizationResult finalizeWithRetry(
            Stock stock,
            LocalDate targetTradeDate,
            BatchSummary summary
    ) {
        KisProperties.Retry retry = kisProperties.getDailyPrice()
                .getUpdate().getRetry();
        long backoff = retry.getInitialBackoffMs();
        for (int attempt = 1; attempt <= retry.getMaxAttempts(); attempt++) {
            applyRequestDelay(summary);
            try {
                summary.apiCalls++;
                return finalizationService.finalizeStock(stock, targetTradeDate);
            } catch (RuntimeException exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                if (attempt == retry.getMaxAttempts()) {
                    throw new RetryExhaustedException(exception);
                }
                sleep(backoff);
                if (attempt < retry.getMaxAttempts() - 1) {
                    backoff = Math.multiplyExact(
                            backoff, retry.getMultiplier());
                }
            }
        }
        throw new IllegalStateException("unreachable retry state");
    }

    private void applyRequestDelay(BatchSummary summary) {
        if (summary.requestMade) {
            sleep(kisProperties.getDailyPrice().getUpdate().getRequestDelayMs());
        }
        summary.requestMade = true;
    }

    private void sleep(long milliseconds) {
        if (milliseconds <= 0) return;
        try {
            sleeper.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DailyPriceFinalizationInterruptedException(exception);
        }
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof KisApiException kisException) {
            return RATE_LIMIT_CODE.equals(kisException.getMessageCode());
        }
        if (exception instanceof ResourceAccessException) return true;
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            return status.value() == 429 || status.is5xxServerError();
        }
        return false;
    }

    private boolean isAuthenticationFailure(RuntimeException exception) {
        if (!(exception instanceof RestClientResponseException responseException)) {
            return false;
        }
        int status = responseException.getStatusCode().value();
        return status == 401 || status == 403;
    }

    private String findKisMessageCode(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof KisApiException kisException) {
                return kisException.getMessageCode();
            }
            current = current.getCause();
        }
        return null;
    }

    private String failureReason(RuntimeException exception) {
        if (exception instanceof RetryExhaustedException) {
            return "RETRY_EXHAUSTED";
        }
        if (exception instanceof KisApiException) {
            return "NON_RETRYABLE_KIS_ERROR";
        }
        return "FINALIZATION_FAILED";
    }

    private void validateConfiguration() {
        KisProperties.Update update = kisProperties.getDailyPrice().getUpdate();
        KisProperties.Retry retry = update.getRetry();
        if (update.getRequestDelayMs() < 0
                || retry.getMaxAttempts() < 1
                || retry.getInitialBackoffMs() < 0
                || retry.getMultiplier() < 1) {
            throw new IllegalStateException(
                    "KIS 일봉 Finalization 설정이 올바르지 않습니다.");
        }
    }

    private static class RetryExhaustedException extends RuntimeException {
        RetryExhaustedException(Throwable cause) {
            super(cause);
        }
    }

    private static class BatchSummary {
        private final LocalDate targetTradeDate;
        private final List<DailyPriceFinalizationTarget> targets;
        private final Instant startedAt;
        private final Map<String, DailyPriceFinalizationResult> results =
                new LinkedHashMap<>();
        private final Map<String, DailyPriceLoadFailure> failures =
                new LinkedHashMap<>();
        private int apiCalls;
        private boolean requestMade;

        BatchSummary(
                LocalDate targetTradeDate,
                List<Stock> stocks,
                Instant startedAt
        ) {
            this.targetTradeDate = targetTradeDate;
            this.targets = stocks.stream()
                    .map(stock -> new DailyPriceFinalizationTarget(
                            stock.getId(), stock.getStockCode(),
                            stock.getStockName()))
                    .toList();
            this.startedAt = startedAt;
        }

        void add(DailyPriceFinalizationResult result) {
            results.put(result.stockCode(), result);
            failures.remove(result.stockCode());
        }

        void addFailure(Stock stock, String reason, String messageCode) {
            failures.put(stock.getStockCode(), new DailyPriceLoadFailure(
                    stock.getStockCode(), stock.getStockName(),
                    reason, messageCode));
        }

        boolean isFailed(String stockCode) {
            return failures.containsKey(stockCode);
        }

        DailyPriceFinalizationBatchResult toResult(Instant finishedAt) {
            int inserted = count(DailyPriceFinalizationStatus.INSERTED);
            int updated = count(DailyPriceFinalizationStatus.UPDATED);
            int unchanged = count(DailyPriceFinalizationStatus.UNCHANGED);
            int noData = count(DailyPriceFinalizationStatus.NO_DATA);
            List<String> noDataStockCodes = targets.stream()
                    .map(DailyPriceFinalizationTarget::stockCode)
                    .filter(code -> results.containsKey(code)
                            && results.get(code).status()
                            == DailyPriceFinalizationStatus.NO_DATA)
                    .toList();
            List<DailyPriceLoadFailure> orderedFailures = targets.stream()
                    .map(DailyPriceFinalizationTarget::stockCode)
                    .filter(failures::containsKey)
                    .map(failures::get)
                    .toList();
            return new DailyPriceFinalizationBatchResult(
                    targetTradeDate, targets.size(), inserted, updated,
                    unchanged, noData, orderedFailures.size(), apiCalls, true,
                    startedAt, finishedAt, orderedFailures,
                    noDataStockCodes, targets);
        }

        private int count(DailyPriceFinalizationStatus status) {
            return (int) results.values().stream()
                    .filter(result -> result.status() == status)
                    .count();
        }
    }
}
