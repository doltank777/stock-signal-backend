package com.stockapp.domain.screening;

import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockDailyPriceRepository;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationalScreeningCompletenessService {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;

    public OperationalScreeningCompletenessResult check(
            LocalDate evaluationDate) {
        if (evaluationDate == null) {
            throw new IllegalArgumentException("evaluationDate is required");
        }

        List<Stock> targets = stockRepository
                .findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        if (targets.isEmpty()) {
            return result(evaluationDate, targets, Set.of());
        }

        List<Long> targetIds = targets.stream().map(Stock::getId).toList();
        Set<Long> availableIds = new HashSet<>(stockDailyPriceRepository
                .findStockIdsWithPriceOnDate(evaluationDate, targetIds));
        return result(evaluationDate, targets, availableIds);
    }

    private OperationalScreeningCompletenessResult result(
            LocalDate evaluationDate,
            List<Stock> targets,
            Set<Long> availableIds
    ) {
        List<OperationalScreeningMissingStock> missingStocks = targets.stream()
                .filter(stock -> !availableIds.contains(stock.getId()))
                .map(stock -> new OperationalScreeningMissingStock(
                        stock.getId(), stock.getStockCode(),
                        stock.getStockName(), stock.getMarketType()))
                .toList();
        int targetStockCount = targets.size();
        int missingStockCount = missingStocks.size();
        int availableStockCount = targetStockCount - missingStockCount;
        OperationalScreeningCompletenessStatus status = targetStockCount > 0
                && missingStockCount == 0
                ? OperationalScreeningCompletenessStatus.COMPLETE
                : OperationalScreeningCompletenessStatus.INCOMPLETE;
        return new OperationalScreeningCompletenessResult(
                evaluationDate, status, targetStockCount,
                availableStockCount, missingStockCount, missingStocks);
    }
}
