package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillFailure;
import com.stockapp.domain.stock.dto.BootstrapMissingHistoryFetchFillResult;
import com.stockapp.domain.stock.dto.DailyHistoryGap;
import com.stockapp.domain.stock.dto.DailyHistoryMissingRange;
import com.stockapp.domain.stock.dto.KisDailyPriceRequestChunk;
import com.stockapp.domain.stock.dto.StockDailyPriceSaveResult;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dto.KisDailyPrice;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BootstrapMissingHistoryFetchFillService {

    private final DailyHistoryGapDetector gapDetector;
    private final DailyHistoryMissingRangePlanner missingRangePlanner;
    private final KisDailyPriceRequestChunkPlanner chunkPlanner;
    private final KisDailyPriceRequestExecutor requestExecutor;
    private final KisDailyPriceClient dailyPriceClient;
    private final StockDailyPriceWriter dailyPriceWriter;
    private final DailyPriceLoadSleeper sleeper;
    private final KisProperties kisProperties;

    public BootstrapMissingHistoryFetchFillResult fetchAndFill(
            Stock stock,
            List<LocalDate> requiredTradingDates
    ) {
        Objects.requireNonNull(stock, "stock is required");
        Objects.requireNonNull(requiredTradingDates,
                "requiredTradingDates is required");

        DailyHistoryGap initialGap = gapDetector.detect(
                stock, requiredTradingDates);
        Summary summary = new Summary(
                stock.getStockCode(), initialGap.missingTradingDates().size());
        if (initialGap.complete()) {
            return summary.completed(List.of());
        }

        List<DailyHistoryMissingRange> ranges = missingRangePlanner.plan(
                initialGap.missingTradingDates());
        List<KisDailyPriceRequestChunk> chunks = ranges.stream()
                .flatMap(range -> chunkPlanner.plan(range).stream())
                .toList();
        summary.plannedRangeCount = ranges.size();
        summary.plannedChunkCount = chunks.size();

        for (KisDailyPriceRequestChunk chunk : chunks) {
            summary.attemptedChunkCount++;
            try {
                List<KisDailyPrice> response = executeRequest(
                        stock, chunk, summary);
                if (response.isEmpty()) {
                    summary.emptyResponseChunkCount++;
                }
                List<KisDailyPrice> accepted = filterAndNormalize(
                        response, chunk, summary);
                if (accepted.isEmpty()) {
                    continue;
                }
                StockDailyPriceSaveResult writeResult =
                        dailyPriceWriter.write(stock, accepted);
                summary.savedRowCount += writeResult.getSavedCount();
                summary.skippedRowCount += writeResult.getSkippedCount();
            } catch (KisDailyPriceRequestInterruptedException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (isAuthenticationFailure(exception)) {
                    throw exception;
                }
                summary.failure = failure(chunk, exception);
                return failedAfterRecheck(
                        stock, requiredTradingDates, summary, exception);
            }
        }

        DailyHistoryGap finalGap = gapDetector.detect(
                stock, requiredTradingDates);
        if (finalGap.complete()) {
            return summary.completed(List.of());
        }
        return summary.partial(finalGap.missingTradingDates());
    }

    private List<KisDailyPrice> executeRequest(
            Stock stock,
            KisDailyPriceRequestChunk chunk,
            Summary summary
    ) {
        KisProperties.Retry retry = kisProperties.getDailyPrice()
                .getUpdate().getRetry();
        KisDailyPriceRequestPolicy policy = new KisDailyPriceRequestPolicy(
                retry.getMaxAttempts(),
                retry.getInitialBackoffMs(),
                retry.getMultiplier());
        return requestExecutor.execute(
                policy,
                () -> dailyPriceClient.getDailyPrices(
                        stock.getStockCode(),
                        chunk.startDate(),
                        chunk.endDate()),
                () -> beforeRequestAttempt(summary))
                .value();
    }

    private void beforeRequestAttempt(Summary summary) {
        if (summary.requestMade) {
            long requestDelayMs = kisProperties.getDailyPrice()
                    .getUpdate().getRequestDelayMs();
            if (requestDelayMs <= 0) {
                summary.apiCallCount++;
                return;
            }
            try {
                sleeper.sleep(requestDelayMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new KisDailyPriceRequestInterruptedException(exception);
            }
        }
        summary.requestMade = true;
        summary.apiCallCount++;
    }

    private List<KisDailyPrice> filterAndNormalize(
            List<KisDailyPrice> response,
            KisDailyPriceRequestChunk chunk,
            Summary summary
    ) {
        Objects.requireNonNull(response, "KIS daily price response is required");
        List<KisDailyPrice> accepted = new ArrayList<>();
        for (KisDailyPrice price : response) {
            LocalDate tradeDate = Objects.requireNonNull(
                    price.getTradeDate(), "KIS daily price tradeDate is required");
            if (tradeDate.isBefore(chunk.startDate())
                    || tradeDate.isAfter(chunk.endDate())) {
                summary.outOfRangeResponseRowCount++;
            } else {
                accepted.add(price);
            }
        }
        return accepted.stream()
                .sorted(Comparator.comparing(KisDailyPrice::getTradeDate))
                .toList();
    }

    private BootstrapMissingHistoryFetchFillResult failedAfterRecheck(
            Stock stock,
            List<LocalDate> requiredTradingDates,
            Summary summary,
            RuntimeException originalFailure
    ) {
        try {
            DailyHistoryGap finalGap = gapDetector.detect(
                    stock, requiredTradingDates);
            if (finalGap.complete()) {
                return summary.completed(List.of());
            }
            return summary.failed(finalGap.missingTradingDates());
        } catch (RuntimeException recheckFailure) {
            originalFailure.addSuppressed(recheckFailure);
            throw originalFailure;
        }
    }

    private BootstrapMissingHistoryFetchFillFailure failure(
            KisDailyPriceRequestChunk chunk,
            RuntimeException exception
    ) {
        int attemptCount = exception instanceof KisDailyPriceRequestExhaustedException exhausted
                ? exhausted.getAttemptCount()
                : 1;
        Throwable detail = exception.getCause() != null
                ? exception.getCause() : exception;
        return new BootstrapMissingHistoryFetchFillFailure(
                chunk,
                detail.getClass().getSimpleName(),
                detail.getMessage(),
                attemptCount);
    }

    private boolean isAuthenticationFailure(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                return status == 401 || status == 403;
            }
            current = current.getCause();
        }
        return false;
    }

    private static class Summary {
        private final String stockCode;
        private final int initialMissingCount;
        private int plannedRangeCount;
        private int plannedChunkCount;
        private int attemptedChunkCount;
        private int apiCallCount;
        private int savedRowCount;
        private int skippedRowCount;
        private int emptyResponseChunkCount;
        private int outOfRangeResponseRowCount;
        private boolean requestMade;
        private BootstrapMissingHistoryFetchFillFailure failure;

        private Summary(String stockCode, int initialMissingCount) {
            this.stockCode = stockCode;
            this.initialMissingCount = initialMissingCount;
        }

        private BootstrapMissingHistoryFetchFillResult completed(
                List<LocalDate> remainingMissingDates) {
            return result(BootstrapMissingHistoryFetchFillStatus.COMPLETED,
                    remainingMissingDates);
        }

        private BootstrapMissingHistoryFetchFillResult partial(
                List<LocalDate> remainingMissingDates) {
            return result(BootstrapMissingHistoryFetchFillStatus.PARTIAL,
                    remainingMissingDates);
        }

        private BootstrapMissingHistoryFetchFillResult failed(
                List<LocalDate> remainingMissingDates) {
            return result(BootstrapMissingHistoryFetchFillStatus.FAILED,
                    remainingMissingDates);
        }

        private BootstrapMissingHistoryFetchFillResult result(
                BootstrapMissingHistoryFetchFillStatus status,
                List<LocalDate> remainingMissingDates) {
            return new BootstrapMissingHistoryFetchFillResult(
                    status,
                    stockCode,
                    initialMissingCount,
                    plannedRangeCount,
                    plannedChunkCount,
                    attemptedChunkCount,
                    apiCallCount,
                    savedRowCount,
                    skippedRowCount,
                    emptyResponseChunkCount,
                    outOfRangeResponseRowCount,
                    remainingMissingDates,
                    failure);
        }
    }
}
