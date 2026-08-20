package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyPriceCompletenessResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationBatchResult;
import com.stockapp.domain.stock.dto.DailyPriceFinalizationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DailyPriceCompletenessEvaluator {

    private final StockDailyPriceRepository stockDailyPriceRepository;

    public DailyPriceCompletenessResult evaluate(
            DailyPriceFinalizationBatchResult batchResult
    ) {
        List<DailyPriceFinalizationTarget> targets = batchResult.targetStocks();
        List<Long> targetIds = targets.stream()
                .map(DailyPriceFinalizationTarget::stockId)
                .toList();
        Set<Long> presentIds = targetIds.isEmpty()
                ? Set.of()
                : new HashSet<>(stockDailyPriceRepository.findStockIdsWithPriceOnDate(
                        batchResult.targetTradeDate(), targetIds));
        List<String> missingStockCodes = targets.stream()
                .filter(target -> !presentIds.contains(target.stockId()))
                .map(DailyPriceFinalizationTarget::stockCode)
                .toList();
        List<String> failedStockCodes = batchResult.failedStocks().stream()
                .map(failure -> failure.stockCode())
                .toList();
        boolean ready = failedStockCodes.isEmpty()
                && missingStockCodes.isEmpty()
                && batchResult.noDataStockCodes().isEmpty();

        return new DailyPriceCompletenessResult(
                batchResult.targetTradeDate(), batchResult.targetStockCount(),
                presentIds.size(), missingStockCodes.size(),
                failedStockCodes.size(), ready, missingStockCodes,
                failedStockCodes, batchResult.noDataStockCodes());
    }
}
