package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.KisMasterSyncExecution;
import com.stockapp.domain.stock.KisMasterSyncExecutionCompletion;
import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.KisMasterSyncExecutionStatus;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMasterStatusEventType;
import com.stockapp.domain.stock.StockMasterStatusHistory;
import com.stockapp.domain.stock.StockMasterStatusHistoryRepository;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KisMasterReconciliationPublisher {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final KisMasterSyncExecutionRepository executionRepository;
    private final StockMasterStatusHistoryRepository historyRepository;
    private final Clock clock;

    @Transactional
    public KisMasterReconciliationResult publish(
            KisMasterSnapshot kospiSnapshot,
            KisMasterSnapshot kosdaqSnapshot,
            Long executionId
    ) {
        validateSnapshots(kospiSnapshot, kosdaqSnapshot);
        KisMasterSyncExecution execution = requireRunningExecution(executionId);
        rejectConcurrentExecution(executionId);

        Instant observedAt = laterOf(
                kospiSnapshot.observedAt(), kosdaqSnapshot.observedAt());
        List<KisMasterNormalizedRecord> masterRecords = new ArrayList<>(
                kospiSnapshot.records().size() + kosdaqSnapshot.records().size());
        masterRecords.addAll(kospiSnapshot.records());
        masterRecords.addAll(kosdaqSnapshot.records());

        List<Stock> stocks = stockRepository
                .findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        Map<String, Stock> stocksByCode = stocks.stream().collect(Collectors.toMap(
                Stock::getStockCode, Function.identity()));
        Map<String, Stock> stocksByStandardCode = stocks.stream()
                .filter(stock -> !isBlank(stock.getStandardCode()))
                .collect(Collectors.toMap(
                        Stock::getStandardCode,
                        Function.identity(),
                        (first, ignored) -> first));

        validateIdentityConflicts(masterRecords, stocksByCode, stocksByStandardCode);

        List<StockMasterStatusHistory> histories = new ArrayList<>();
        Set<String> observedCodes = new HashSet<>();
        int existingMatched = 0;
        int newStocks = 0;
        int updatedStocks = 0;
        int unchangedStocks = 0;
        int reappearedStocks = 0;

        for (KisMasterNormalizedRecord record : masterRecords) {
            observedCodes.add(record.stockCode());
            Stock stock = stocksByCode.get(record.stockCode());
            if (stock == null) {
                if (!record.instrumentSupported()) {
                    continue;
                }
                stock = Stock.createFromMaster(
                        record.stockCode(), record.stockName(), record.market());
                stock.applyMasterState(
                        record.stockName(), record.market(), record.standardCode(),
                        record.instrumentType(), record.securityGroupCode(),
                        record.preferredStockCode(), record.etpProductCode(),
                        record.listingDate(), record.spac(), record.suspended(),
                        record.liquidationTrading(), record.managedIssue(),
                        observedAt, execution);
                stockRepository.save(stock);
                histories.add(history(stock, StockMasterStatusEventType.MASTER_FIRST_SEEN,
                        null, "true", observedAt, execution));
                stocksByCode.put(record.stockCode(), stock);
                newStocks++;
                continue;
            }

            existingMatched++;
            boolean baseline = !stock.hasMasterBaseline();
            boolean reappeared = Boolean.FALSE.equals(stock.getPresentInLatestMaster());
            boolean changed = masterStateChanged(stock, record);
            if (baseline) {
                histories.add(history(stock, StockMasterStatusEventType.MASTER_FIRST_SEEN,
                        null, "true", observedAt, execution));
            } else {
                if (reappeared) {
                    histories.add(history(stock, StockMasterStatusEventType.MASTER_REAPPEARED,
                            "false", "true", observedAt, execution));
                    reappearedStocks++;
                }
                appendFieldChanges(histories, stock, record, observedAt, execution);
            }
            if (baseline || changed) {
                stock.applyMasterState(
                        record.stockName(), record.market(), record.standardCode(),
                        record.instrumentType(), record.securityGroupCode(),
                        record.preferredStockCode(), record.etpProductCode(),
                        record.listingDate(), record.spac(), record.suspended(),
                        record.liquidationTrading(), record.managedIssue(),
                        observedAt, execution);
                updatedStocks++;
            } else {
                unchangedStocks++;
            }
        }

        int missingStocks = 0;
        for (Stock stock : stocks) {
            if (observedCodes.contains(stock.getStockCode())) {
                continue;
            }
            boolean firstMissingObservation = stock.getPresentInLatestMaster() == null;
            boolean newlyMissing = !Boolean.FALSE.equals(stock.getPresentInLatestMaster());
            if (firstMissingObservation || newlyMissing) {
                histories.add(history(
                        stock,
                        StockMasterStatusEventType.MASTER_MISSING_OBSERVED,
                        value(stock.getPresentInLatestMaster()),
                        "false",
                        observedAt,
                        execution));
            }
            if (newlyMissing || !Objects.equals(stock.getMasterObservedAt(), observedAt)) {
                stock.observeMissingFromMaster(observedAt, execution);
            }
            missingStocks++;
        }

        historyRepository.saveAll(histories);
        execution.complete(completion(kospiSnapshot, kosdaqSnapshot, observedAt),
                Instant.now(clock));

        int supported = kospiSnapshot.supportedInstrumentCount()
                + kosdaqSnapshot.supportedInstrumentCount();
        int total = masterRecords.size();
        return new KisMasterReconciliationResult(
                total,
                supported,
                existingMatched,
                newStocks,
                updatedStocks,
                unchangedStocks,
                missingStocks,
                reappearedStocks,
                total - supported,
                0,
                histories.size());
    }

    private void validateSnapshots(
            KisMasterSnapshot kospiSnapshot,
            KisMasterSnapshot kosdaqSnapshot
    ) {
        if (kospiSnapshot == null || kosdaqSnapshot == null
                || kospiSnapshot.market() != MarketType.KOSPI
                || kosdaqSnapshot.market() != MarketType.KOSDAQ
                || !kospiSnapshot.publishable()
                || !kosdaqSnapshot.publishable()) {
            throw new KisMasterSnapshotNotReadyException(
                    "Both KOSPI and KOSDAQ Master snapshots must be READY");
        }
        Set<String> codes = new HashSet<>();
        for (KisMasterNormalizedRecord record : concat(kospiSnapshot, kosdaqSnapshot)) {
            if (!codes.add(record.stockCode())) {
                throw new KisMasterIdentityConflictException(
                        "Duplicate stockCode across Master snapshots: " + record.stockCode());
            }
        }
    }

    private KisMasterSyncExecution requireRunningExecution(Long executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("A persisted RUNNING execution is required");
        }
        KisMasterSyncExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Master sync execution does not exist: " + executionId));
        if (execution.getStatus() != KisMasterSyncExecutionStatus.RUNNING) {
            throw new IllegalStateException("Master sync execution is not running");
        }
        return execution;
    }

    private void rejectConcurrentExecution(Long executionId) {
        boolean anotherRunning = executionRepository
                .findWithLockByStatusOrderByStartedAtAsc(
                        KisMasterSyncExecutionStatus.RUNNING)
                .stream()
                .anyMatch(candidate -> !candidate.getId().equals(executionId));
        if (anotherRunning) {
            throw new KisMasterConcurrentExecutionException(
                    "Another Master sync execution is already running");
        }
    }

    private void validateIdentityConflicts(
            List<KisMasterNormalizedRecord> records,
            Map<String, Stock> stocksByCode,
            Map<String, Stock> stocksByStandardCode
    ) {
        for (KisMasterNormalizedRecord record : records) {
            if (!record.instrumentSupported() || stocksByCode.containsKey(record.stockCode())) {
                continue;
            }
            Stock sameStandardCode = stocksByStandardCode.get(record.standardCode());
            if (sameStandardCode != null) {
                throw new KisMasterIdentityConflictException(
                        "New stockCode " + record.stockCode()
                                + " conflicts with existing stockCode "
                                + sameStandardCode.getStockCode()
                                + " for standardCode " + record.standardCode());
            }
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

    private void appendFieldChanges(
            List<StockMasterStatusHistory> histories,
            Stock stock,
            KisMasterNormalizedRecord record,
            Instant observedAt,
            KisMasterSyncExecution execution
    ) {
        addChange(histories, stock, StockMasterStatusEventType.STOCK_NAME_CHANGED,
                stock.getStockName(), record.stockName(), observedAt, execution);
        addChange(histories, stock, StockMasterStatusEventType.MARKET_CHANGED,
                stock.getMarketType(), record.market(), observedAt, execution);
        addChange(histories, stock, StockMasterStatusEventType.STANDARD_CODE_CHANGED,
                stock.getStandardCode(), record.standardCode(), observedAt, execution);
        addChange(histories, stock, StockMasterStatusEventType.INSTRUMENT_TYPE_CHANGED,
                stock.getInstrumentType(), record.instrumentType(), observedAt, execution);
        addChange(histories, stock, StockMasterStatusEventType.SUSPENDED_CHANGED,
                stock.getSuspended(), record.suspended(), observedAt, execution);
        addChange(histories, stock, StockMasterStatusEventType.LIQUIDATION_TRADING_CHANGED,
                stock.getLiquidationTrading(), record.liquidationTrading(), observedAt, execution);
        addChange(histories, stock, StockMasterStatusEventType.MANAGED_ISSUE_CHANGED,
                stock.getManagedIssue(), record.managedIssue(), observedAt, execution);
        addChange(histories, stock, StockMasterStatusEventType.SPAC_CHANGED,
                stock.getSpac(), record.spac(), observedAt, execution);
    }

    private void addChange(
            List<StockMasterStatusHistory> histories,
            Stock stock,
            StockMasterStatusEventType eventType,
            Object oldValue,
            Object newValue,
            Instant observedAt,
            KisMasterSyncExecution execution
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            histories.add(history(stock, eventType, value(oldValue), value(newValue),
                    observedAt, execution));
        }
    }

    private StockMasterStatusHistory history(
            Stock stock,
            StockMasterStatusEventType eventType,
            String oldValue,
            String newValue,
            Instant observedAt,
            KisMasterSyncExecution execution
    ) {
        return StockMasterStatusHistory.create(
                stock, eventType, oldValue, newValue, observedAt, null, execution);
    }

    private KisMasterSyncExecutionCompletion completion(
            KisMasterSnapshot kospi,
            KisMasterSnapshot kosdaq,
            Instant observedAt
    ) {
        return new KisMasterSyncExecutionCompletion(
                observedAt,
                kospi.rawParsedRowCount(),
                kosdaq.rawParsedRowCount(),
                kospi.supportedInstrumentCount() + kosdaq.supportedInstrumentCount(),
                kospi.unsupportedInstrumentCount() + kosdaq.unsupportedInstrumentCount(),
                kospi.validation().unknownInstrumentCount()
                        + kosdaq.validation().unknownInstrumentCount(),
                kospi.validation().duplicateShortCodeCount()
                        + kosdaq.validation().duplicateShortCodeCount(),
                0);
    }

    private List<KisMasterNormalizedRecord> concat(
            KisMasterSnapshot first,
            KisMasterSnapshot second
    ) {
        List<KisMasterNormalizedRecord> records = new ArrayList<>(
                first.records().size() + second.records().size());
        records.addAll(first.records());
        records.addAll(second.records());
        return records;
    }

    private Instant laterOf(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private String value(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
