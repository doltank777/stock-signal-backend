package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceInitialLoadResult;
import com.stockapp.domain.stock.dto.DailyPriceLoadFailure;
import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPriceInitialLoader {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String RATE_LIMIT_CODE = "EGW00201";

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final StockDailyPriceWriter stockDailyPriceWriter;
    private final KisDailyPriceClient kisDailyPriceClient;
    private final KisProperties kisProperties;
    private final DailyPriceLoadSleeper sleeper;
    private final Clock clock;

    public DailyPriceInitialLoadResult load() {
        return load(LocalDate.now(clock.withZone(KOREA_ZONE)).minusDays(1));
    }

    public DailyPriceInitialLoadResult load(LocalDate baseEndDate) {
        validateConfiguration();
        KisProperties.DailyPrice config = kisProperties.getDailyPrice();
        List<Stock> stocks = stockRepository.findByMarketTypeInOrderByIdAsc(
                List.of(MarketType.KOSPI, MarketType.KOSDAQ));
        LoadSummary summary = new LoadSummary(stocks.size(), Instant.now(clock));

        log.info("일봉 최초 적재 시작 - 기준일: {}, 대상 종목 수: {}, target: {}, requestDelayMs: {}",
                baseEndDate, stocks.size(), config.getTargetTradingDays(), config.getRequestDelayMs());

        for (int index = 0; index < stocks.size(); index++) {
            Stock stock = stocks.get(index);
            try {
                loadStock(stock, baseEndDate, summary);
            } catch (BatchInterruptedException e) {
                log.warn("일봉 최초 적재가 인터럽트되어 중단됩니다.");
                throw e;
            } catch (Exception e) {
                summary.failed++;
                String code = e instanceof KisApiException kisException
                        ? kisException.getMessageCode() : null;
                summary.failures.add(new DailyPriceLoadFailure(
                        stock.getStockCode(), stock.getStockName(),
                        "REQUEST_FAILED", code));
                log.error("일봉 최초 적재 종목 실패 - stockCode: {}, messageCode: {}",
                        stock.getStockCode(), code, e);
            }

            int processed = index + 1;
            if (processed % config.getProgressLogInterval() == 0
                    || processed == stocks.size()) {
                log.info("일봉 최초 적재 진행 - {}/{}", processed, stocks.size());
            }
        }

        DailyPriceInitialLoadResult result = summary.toResult(Instant.now(clock));
        log.info("일봉 최초 적재 완료 - completed: {}, skipped: {}, partial: {}, failed: {}, apiCalls: {}, elapsedMs: {}",
                result.getCompletedStockCount(), result.getSkippedStockCount(),
                result.getPartialHistoryStockCount(), result.getFailedStockCount(),
                result.getApiCallCount(), Duration.between(result.getStartedAt(), result.getFinishedAt()).toMillis());
        return result;
    }

    private void loadStock(Stock stock, LocalDate baseEndDate, LoadSummary summary) {
        KisProperties.DailyPrice config = kisProperties.getDailyPrice();
        long existingCount = stockDailyPriceRepository
                .countByStockAndTradeDateLessThanEqual(stock, baseEndDate);
        if (existingCount >= config.getTargetTradingDays()) {
            summary.skipped++;
            return;
        }

        long availableCount = existingCount;
        LocalDate requestEndDate = baseEndDate;
        LocalDate minimumEndDate = baseEndDate.minusYears(config.getMaxLookbackYears());
        LocalDate previousOldestDate = null;
        int stockApiCalls = 0;

        while (availableCount < config.getTargetTradingDays()) {
            if (stockApiCalls >= config.getMaxApiCallsPerStock()) {
                markPartial(stock, summary, "MAX_API_CALLS");
                return;
            }
            if (requestEndDate.isBefore(minimumEndDate)) {
                markPartial(stock, summary, "MAX_LOOKBACK");
                return;
            }

            LocalDate requestStartDate = requestEndDate
                    .minusMonths(config.getRequestWindowMonths());
            if (requestStartDate.isBefore(minimumEndDate)) {
                requestStartDate = minimumEndDate;
            }
            log.debug("KIS 일봉 요청 - stockCode: {}, startDate: {}, endDate: {}",
                    stock.getStockCode(), requestStartDate, requestEndDate);
            RequestResult request = requestWithRetry(
                    stock, requestStartDate, requestEndDate, summary,
                    config.getMaxApiCallsPerStock() - stockApiCalls);
            stockApiCalls += request.apiCalls();
            List<KisDailyPrice> prices = request.prices();
            summary.requested += prices.size();

            if (prices.isEmpty()) {
                markPartial(stock, summary, "AVAILABLE_HISTORY_EXHAUSTED");
                return;
            }

            StockDailyPriceSaveResult writeResult = stockDailyPriceWriter.write(stock, prices);
            summary.saved += writeResult.getSavedCount();
            summary.dailySkipped += writeResult.getSkippedCount();
            availableCount += writeResult.getSavedCount();
            log.debug("일봉 저장 - stockCode: {}, requested: {}, saved: {}, skipped: {}",
                    stock.getStockCode(), writeResult.getRequestedCount(),
                    writeResult.getSavedCount(), writeResult.getSkippedCount());

            if (availableCount >= config.getTargetTradingDays()) {
                summary.completed++;
                return;
            }

            LocalDate oldestDate = prices.stream()
                    .map(KisDailyPrice::getTradeDate)
                    .distinct()
                    .min(LocalDate::compareTo)
                    .orElseThrow();
            if (previousOldestDate != null && !oldestDate.isBefore(previousOldestDate)) {
                markPartial(stock, summary, "NO_DATE_PROGRESS");
                return;
            }
            previousOldestDate = oldestDate;
            requestEndDate = oldestDate.minusDays(1);
        }
    }

    private RequestResult requestWithRetry(
            Stock stock, LocalDate startDate, LocalDate endDate,
            LoadSummary summary, int remainingApiCalls) {
        int attempts = Math.min(
                kisProperties.getDailyPrice().getRetryMaxAttempts(),
                remainingApiCalls);
        int apiCalls = 0;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            applyRequestDelay(summary);
            try {
                apiCalls++;
                summary.apiCalls++;
                return new RequestResult(kisDailyPriceClient.getDailyPrices(
                        stock.getStockCode(), startDate, endDate), apiCalls);
            } catch (RuntimeException e) {
                if (!isRetryable(e) || attempt == attempts) {
                    throw e;
                }
                long backoff = kisProperties.getDailyPrice().getRetryInitialDelayMs()
                        * (1L << (attempt - 1));
                log.warn("KIS 일봉 요청 재시도 - stockCode: {}, attempt: {}/{}, delayMs: {}",
                        stock.getStockCode(), attempt + 1, attempts, backoff);
                sleep(backoff);
            }
        }
        throw new IllegalStateException("도달할 수 없는 retry 상태입니다.");
    }

    private void applyRequestDelay(LoadSummary summary) {
        if (summary.requestMade) {
            sleep(kisProperties.getDailyPrice().getRequestDelayMs());
        }
        summary.requestMade = true;
    }

    private void sleep(long milliseconds) {
        if (milliseconds <= 0) return;
        try {
            sleeper.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BatchInterruptedException(e);
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

    private void markPartial(Stock stock, LoadSummary summary, String reason) {
        summary.partial++;
        log.warn("일봉 최초 적재 부분 종료 - stockCode: {}, reason: {}",
                stock.getStockCode(), reason);
    }

    private void validateConfiguration() {
        KisProperties.DailyPrice c = kisProperties.getDailyPrice();
        if (kisProperties.getBaseUrl() == null || kisProperties.getBaseUrl().isBlank()
                || kisProperties.getAppKey() == null || kisProperties.getAppKey().isBlank()
                || kisProperties.getAppSecret() == null || kisProperties.getAppSecret().isBlank()) {
            throw new IllegalStateException("KIS 필수 설정이 누락되었습니다.");
        }
        if (c.getTargetTradingDays() <= 0 || c.getRequestWindowMonths() <= 0
                || c.getRetryMaxAttempts() <= 0 || c.getMaxApiCallsPerStock() <= 0
                || c.getMaxLookbackYears() <= 0 || c.getProgressLogInterval() <= 0
                || c.getRequestDelayMs() < 0 || c.getRetryInitialDelayMs() < 0) {
            throw new IllegalStateException("KIS 일봉 최초 적재 설정이 올바르지 않습니다.");
        }
    }

    private record RequestResult(List<KisDailyPrice> prices, int apiCalls) {}

    private static class BatchInterruptedException extends RuntimeException {
        BatchInterruptedException(Throwable cause) { super(cause); }
    }

    private static class LoadSummary {
        private final int targetStocks;
        private final Instant startedAt;
        private final List<DailyPriceLoadFailure> failures = new ArrayList<>();
        private int completed;
        private int skipped;
        private int partial;
        private int failed;
        private int requested;
        private int saved;
        private int dailySkipped;
        private int apiCalls;
        private boolean requestMade;

        LoadSummary(int targetStocks, Instant startedAt) {
            this.targetStocks = targetStocks;
            this.startedAt = startedAt;
        }

        DailyPriceInitialLoadResult toResult(Instant finishedAt) {
            return DailyPriceInitialLoadResult.builder()
                    .targetStockCount(targetStocks).completedStockCount(completed)
                    .skippedStockCount(skipped).partialHistoryStockCount(partial)
                    .failedStockCount(failed).requestedDailyPriceCount(requested)
                    .savedDailyPriceCount(saved).skippedDailyPriceCount(dailySkipped)
                    .apiCallCount(apiCalls).startedAt(startedAt).finishedAt(finishedAt)
                    .failures(List.copyOf(failures)).build();
        }
    }
}
