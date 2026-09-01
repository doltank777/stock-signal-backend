package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.KisMasterSyncExecutionStatus;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KisMasterReconciliationPlanner {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final KisMasterSyncExecutionRepository executionRepository;

    @Transactional(readOnly = true)
    public KisMasterReconciliationPlan plan(
            KisMasterSnapshot kospiSnapshot,
            KisMasterSnapshot kosdaqSnapshot
    ) {
        requireExpectedMarkets(kospiSnapshot, kosdaqSnapshot);
        List<KisMasterNormalizedRecord> records = concat(kospiSnapshot, kosdaqSnapshot);
        List<Stock> stocks = stockRepository
                .findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        Map<String, Stock> stocksByCode = stocks.stream().collect(Collectors.toMap(
                Stock::getStockCode, Function.identity()));
        Map<String, Stock> stocksByStandardCode = stocks.stream()
                .filter(stock -> !isBlank(stock.getStandardCode()))
                .collect(Collectors.toMap(
                        Stock::getStandardCode, Function.identity(),
                        (first, ignored) -> first));

        List<KisMasterReconciliationCandidate> newStocks = new ArrayList<>();
        List<KisMasterIdentityConflict> conflicts = new ArrayList<>();
        Set<String> observedCodes = new HashSet<>();
        int supported = 0;
        int matched = 0;
        int existingUnsupported = 0;
        int estimatedUpdated = 0;
        int unchanged = 0;
        int reappeared = 0;

        for (KisMasterNormalizedRecord record : records) {
            observedCodes.add(record.stockCode());
            if (record.instrumentSupported()) {
                supported++;
            }
            Stock stock = stocksByCode.get(record.stockCode());
            if (stock != null) {
                matched++;
                if (!record.instrumentSupported()) {
                    existingUnsupported++;
                }
                if (!stock.hasMasterBaseline() || masterStateChanged(stock, record)) {
                    estimatedUpdated++;
                } else {
                    unchanged++;
                }
                if (Boolean.FALSE.equals(stock.getPresentInLatestMaster())) {
                    reappeared++;
                }
                continue;
            }
            if (!record.instrumentSupported()) {
                continue;
            }
            Stock sameStandardCode = stocksByStandardCode.get(record.standardCode());
            if (sameStandardCode != null) {
                conflicts.add(new KisMasterIdentityConflict(
                        record.stockCode(), record.standardCode(),
                        sameStandardCode.getId(), sameStandardCode.getStockCode(),
                        sameStandardCode.getStandardCode()));
            } else {
                newStocks.add(candidate(record));
            }
        }

        List<KisMasterMissingStock> missing = stocks.stream()
                .filter(stock -> !observedCodes.contains(stock.getStockCode()))
                .map(stock -> new KisMasterMissingStock(
                        stock.getStockCode(), stock.getStockName(),
                        stock.getMarketType(), stock.getPresentInLatestMaster()))
                .toList();
        int running = executionRepository
                .findByStatusOrderByStartedAtAsc(KisMasterSyncExecutionStatus.RUNNING)
                .size();

        return new KisMasterReconciliationPlan(
                kospiSnapshot.publishable(), kosdaqSnapshot.publishable(),
                records.size(), supported, records.size() - supported,
                stocks.size(), matched, existingUnsupported,
                estimatedUpdated, unchanged, reappeared, running,
                newStocks, missing, conflicts);
    }

    private void requireExpectedMarkets(
            KisMasterSnapshot kospiSnapshot,
            KisMasterSnapshot kosdaqSnapshot
    ) {
        if (kospiSnapshot == null || kosdaqSnapshot == null
                || kospiSnapshot.market() != MarketType.KOSPI
                || kosdaqSnapshot.market() != MarketType.KOSDAQ) {
            throw new IllegalArgumentException(
                    "KOSPI and KOSDAQ Master snapshots are required");
        }
    }

    private boolean masterStateChanged(Stock stock, KisMasterNormalizedRecord record) {
        return !Boolean.TRUE.equals(stock.getPresentInLatestMaster())
                || !Objects.equals(stock.getStockName(), record.stockName())
                || stock.getMarketType() != record.market()
                || !Objects.equals(stock.getStandardCode(), record.standardCode())
                || stock.getInstrumentType() != record.instrumentType()
                || !Objects.equals(stock.getSecurityGroupCode(), record.securityGroupCode())
                || !Objects.equals(stock.getPreferredStockCode(), record.preferredStockCode())
                || !Objects.equals(stock.getEtpProductCode(), record.etpProductCode())
                || !Objects.equals(stock.getListingDate(), record.listingDate())
                || !Objects.equals(stock.getSpac(), record.spac())
                || !Objects.equals(stock.getSuspended(), record.suspended())
                || !Objects.equals(stock.getLiquidationTrading(), record.liquidationTrading())
                || !Objects.equals(stock.getManagedIssue(), record.managedIssue());
    }

    private KisMasterReconciliationCandidate candidate(KisMasterNormalizedRecord record) {
        return new KisMasterReconciliationCandidate(
                record.stockCode(), record.stockName(), record.market(),
                record.instrumentType(), record.listingDate(), record.suspended(),
                record.liquidationTrading());
    }

    private List<KisMasterNormalizedRecord> concat(
            KisMasterSnapshot kospi,
            KisMasterSnapshot kosdaq
    ) {
        List<KisMasterNormalizedRecord> records = new ArrayList<>(
                kospi.records().size() + kosdaq.records().size());
        records.addAll(kospi.records());
        records.addAll(kosdaq.records());
        return records;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
