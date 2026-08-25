package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceLoadFailure;
import com.stockapp.domain.stock.dto.DailyPriceUpdateResult;
import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisApiException;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceUpdateService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final StockDailyPriceWriter stockDailyPriceWriter;
    private final KisDailyPriceClient kisDailyPriceClient;
    private final KisProperties kisProperties;
    private final DailyPriceLoadSleeper sleeper;
    private final Clock clock;
    private final KisDailyPriceRequestExecutor requestExecutor;

    public DailyPriceUpdateResult update() {
        return update(LocalDate.now(clock.withZone(KOREA_ZONE)));
    }

    public DailyPriceUpdateResult update(LocalDate baseDate) {
        return update(baseDate, null);
    }

    public DailyPriceUpdateResult updateStock(String stockCode) {
        return updateStock(stockCode, LocalDate.now(clock.withZone(KOREA_ZONE)));
    }

    public DailyPriceUpdateResult updateStock(String stockCode, LocalDate baseDate) {
        if (stockCode == null || stockCode.isBlank()) {
            return update(baseDate);
        }
        return update(baseDate, stockCode.trim());
    }

    private DailyPriceUpdateResult update(LocalDate baseDate, String stockCode) {
        if (baseDate == null) {
            throw new IllegalArgumentException("일봉 업데이트 기준일은 필수입니다.");
        }
        validateConfiguration();
        LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));

        List<Stock> stocks = selectTargetStocks(stockCode);
        UpdateSummary summary = new UpdateSummary(baseDate, stocks.size(), Instant.now(clock));
        int progressLogInterval = Math.max(
                1, kisProperties.getDailyPrice().getProgressLogInterval());

        log.info("일봉 업데이트 시작 - 기준일: {}, 대상 종목 수: {}",
                baseDate, stocks.size());

        for (int index = 0; index < stocks.size(); index++) {
            Stock stock = stocks.get(index);
            try {
                updateStock(stock, baseDate, today, summary);
            } catch (BatchInterruptedException e) {
                log.warn("일봉 업데이트가 인터럽트되어 중단됩니다.");
                throw e;
            } catch (RuntimeException e) {
                if (isAuthenticationFailure(e)) {
                    log.error("KIS 인증 오류로 일봉 업데이트를 중단합니다. status: {}",
                            ((RestClientResponseException) e).getStatusCode().value());
                    throw e;
                }
                summary.failed++;
                String messageCode = findKisMessageCode(e);
                summary.failures.add(new DailyPriceLoadFailure(
                        stock.getStockCode(), stock.getStockName(),
                        failureReason(e), messageCode));
                log.error("일봉 업데이트 종목 실패 - stockCode: {}, messageCode: {}, error: {}",
                        stock.getStockCode(), messageCode, e.getMessage(), e);
            }

            int processed = index + 1;
            if (processed % progressLogInterval == 0 || processed == stocks.size()) {
                log.info("일봉 업데이트 진행 - {}/{}", processed, stocks.size());
            }
        }

        DailyPriceUpdateResult result = summary.toResult(Instant.now(clock));
        log.info("일봉 업데이트 완료 - updated: {}, upToDate: {}, noNewData: {}, "
                        + "noBaseHistory: {}, failed: {}, apiCalls: {}, savedRows: {}, elapsedMs: {}",
                result.getUpdatedStockCount(), result.getUpToDateStockCount(),
                result.getNoNewDataStockCount(), result.getNoBaseHistoryStockCount(),
                result.getFailedStockCount(), result.getApiCallCount(),
                result.getSavedDailyPriceCount(),
                Duration.between(result.getStartedAt(), result.getFinishedAt()).toMillis());
        return result;
    }

    private List<Stock> selectTargetStocks(String stockCode) {
        if (stockCode == null) {
            return stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        }
        Stock stock = stockRepository.findByStockCodeAndMarketTypeIn(stockCode, TARGET_MARKETS)
                .orElseThrow(() -> new IllegalArgumentException(
                        "KOSPI/KOSDAQ 일봉 업데이트 대상 종목을 찾을 수 없습니다: " + stockCode));
        return List.of(stock);
    }

    private void updateStock(
            Stock stock,
            LocalDate baseDate,
            LocalDate today,
            UpdateSummary summary
    ) {
        LocalDate latestTradeDate = stockDailyPriceRepository
                .findLatestTradeDateByStock(stock)
                .orElse(null);
        if (latestTradeDate == null) {
            summary.noBaseHistory++;
            return;
        }
        if (!latestTradeDate.isBefore(baseDate)) {
            summary.upToDate++;
            return;
        }

        long catchUpDays = ChronoUnit.DAYS.between(latestTradeDate, baseDate);
        int maxCatchUpDays = kisProperties.getDailyPrice().getUpdate().getMaxCatchUpDays();
        if (catchUpDays > maxCatchUpDays) {
            summary.failed++;
            summary.failures.add(new DailyPriceLoadFailure(
                    stock.getStockCode(), stock.getStockName(),
                    "MAX_CATCH_UP_DAYS", null));
            log.warn("일봉 업데이트 catch-up 범위 초과 - stockCode: {}, catchUpDays: {}, maxCatchUpDays: {}",
                    stock.getStockCode(), catchUpDays, maxCatchUpDays);
            return;
        }

        LocalDate requestStartDate = latestTradeDate.plusDays(1);
        List<KisDailyPrice> prices = requestWithRetry(
                        stock, requestStartDate, baseDate, summary)
                .stream()
                .filter(price -> !price.getTradeDate().isBefore(requestStartDate)
                        && !price.getTradeDate().isAfter(baseDate)
                        && !price.getTradeDate().equals(today))
                .sorted(Comparator.comparing(KisDailyPrice::getTradeDate))
                .toList();

        if (prices.isEmpty()) {
            summary.noNewData++;
            return;
        }

        StockDailyPriceSaveResult writeResult = stockDailyPriceWriter.write(stock, prices);
        summary.saved += writeResult.getSavedCount();
        if (writeResult.getSavedCount() > 0) {
            summary.updated++;
        } else {
            summary.noNewData++;
        }
    }

    private List<KisDailyPrice> requestWithRetry(
            Stock stock, LocalDate startDate, LocalDate endDate,
            UpdateSummary summary) {
        KisProperties.Retry retry = kisProperties.getDailyPrice().getUpdate().getRetry();
        KisDailyPriceRequestPolicy policy = new KisDailyPriceRequestPolicy(
                retry.getMaxAttempts(), retry.getInitialBackoffMs(),
                retry.getMultiplier());
        try {
            return requestExecutor.execute(
                    policy,
                    () -> kisDailyPriceClient.getDailyPrices(
                            stock.getStockCode(), startDate, endDate),
                    () -> {
                        applyRequestDelay(summary);
                        summary.apiCalls++;
                    })
                    .value();
        } catch (KisDailyPriceRequestExhaustedException exception) {
            throw new RetryExhaustedException(
                    (RuntimeException) exception.getCause());
        } catch (KisDailyPriceRequestInterruptedException exception) {
            throw new BatchInterruptedException(exception);
        }
    }

    private void applyRequestDelay(UpdateSummary summary) {
        if (summary.requestMade) {
            sleep(kisProperties.getDailyPrice().getUpdate().getRequestDelayMs());
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
        if (exception instanceof RetryExhaustedException) return "RETRY_EXHAUSTED";
        if (exception instanceof KisApiException) return "NON_RETRYABLE_KIS_ERROR";
        return "UPDATE_FAILED";
    }

    private void validateConfiguration() {
        KisProperties.Update update = kisProperties.getDailyPrice().getUpdate();
        KisProperties.Retry retry = update.getRetry();
        if (kisProperties.getBaseUrl() == null || kisProperties.getBaseUrl().isBlank()
                || kisProperties.getAppKey() == null || kisProperties.getAppKey().isBlank()
                || kisProperties.getAppSecret() == null || kisProperties.getAppSecret().isBlank()) {
            throw new IllegalStateException("KIS 필수 설정이 누락되었습니다.");
        }
        if (update.getRequestDelayMs() < 0 || update.getMaxCatchUpDays() < 1
                || retry.getMaxAttempts() < 1 || retry.getInitialBackoffMs() < 0
                || retry.getMultiplier() < 1) {
            throw new IllegalStateException("KIS 일봉 업데이트 설정이 올바르지 않습니다.");
        }
        long backoff = retry.getInitialBackoffMs();
        try {
            for (int attempt = 1; attempt < retry.getMaxAttempts() - 1; attempt++) {
                backoff = Math.multiplyExact(backoff, retry.getMultiplier());
            }
        } catch (ArithmeticException e) {
            throw new IllegalStateException("KIS 일봉 업데이트 backoff 설정이 너무 큽니다.", e);
        }
    }

    private static class RetryExhaustedException extends RuntimeException {
        RetryExhaustedException(Throwable cause) { super(cause); }
    }

    private static class BatchInterruptedException extends RuntimeException {
        BatchInterruptedException(Throwable cause) { super(cause); }
    }

    private static class UpdateSummary {
        private final LocalDate baseDate;
        private final int targetStocks;
        private final Instant startedAt;
        private final List<DailyPriceLoadFailure> failures = new ArrayList<>();
        private int updated;
        private int upToDate;
        private int noNewData;
        private int noBaseHistory;
        private int failed;
        private int apiCalls;
        private int saved;
        private boolean requestMade;

        UpdateSummary(LocalDate baseDate, int targetStocks, Instant startedAt) {
            this.baseDate = baseDate;
            this.targetStocks = targetStocks;
            this.startedAt = startedAt;
        }

        DailyPriceUpdateResult toResult(Instant finishedAt) {
            return DailyPriceUpdateResult.builder()
                    .baseDate(baseDate).targetStockCount(targetStocks)
                    .updatedStockCount(updated).upToDateStockCount(upToDate)
                    .noNewDataStockCount(noNewData)
                    .noBaseHistoryStockCount(noBaseHistory)
                    .failedStockCount(failed).apiCallCount(apiCalls)
                    .savedDailyPriceCount(saved).startedAt(startedAt)
                    .finishedAt(finishedAt).failures(List.copyOf(failures))
                    .build();
        }
    }
}
