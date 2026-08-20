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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceFinalizationBatchService {

    private static final String RATE_LIMIT_CODE = "EGW00201";
    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final DailyPriceFinalizationService finalizationService;
    private final DailyPriceCompletenessEvaluator completenessEvaluator;
    private final KisProperties kisProperties;
    private final DailyPriceLoadSleeper sleeper;
    private final Clock clock;

    public DailyPriceFinalizationExecutionResult finalizeAll(
            LocalDate targetTradeDate
    ) {
        if (targetTradeDate == null) {
            throw new IllegalArgumentException("targetTradeDate is required");
        }
        validateConfiguration();

        List<Stock> stocks = stockRepository.findByMarketTypeInOrderByIdAsc(
                TARGET_MARKETS);
        BatchSummary summary = new BatchSummary(
                targetTradeDate, stocks, Instant.now(clock));
        log.info("일봉 Finalization batch 시작 - targetTradeDate: {}, target: {}",
                targetTradeDate, stocks.size());

        for (Stock stock : stocks) {
            try {
                DailyPriceFinalizationResult result = finalizeWithRetry(
                        stock, targetTradeDate, summary);
                summary.add(result);
            } catch (BatchInterruptedException exception) {
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
            throw new BatchInterruptedException(exception);
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

    private static class BatchInterruptedException extends RuntimeException {
        BatchInterruptedException(Throwable cause) {
            super(cause);
        }
    }

    private static class BatchSummary {
        private final LocalDate targetTradeDate;
        private final List<DailyPriceFinalizationTarget> targets;
        private final Instant startedAt;
        private final List<DailyPriceLoadFailure> failures = new ArrayList<>();
        private final List<String> noDataStockCodes = new ArrayList<>();
        private int inserted;
        private int updated;
        private int unchanged;
        private int noData;
        private int failed;
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
            switch (result.status()) {
                case INSERTED -> inserted++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
                case NO_DATA -> {
                    noData++;
                    noDataStockCodes.add(result.stockCode());
                }
            }
        }

        void addFailure(Stock stock, String reason, String messageCode) {
            failed++;
            failures.add(new DailyPriceLoadFailure(
                    stock.getStockCode(), stock.getStockName(),
                    reason, messageCode));
        }

        DailyPriceFinalizationBatchResult toResult(Instant finishedAt) {
            return new DailyPriceFinalizationBatchResult(
                    targetTradeDate, targets.size(), inserted, updated,
                    unchanged, noData, failed, apiCalls, true,
                    startedAt, finishedAt, failures, noDataStockCodes, targets);
        }
    }
}
