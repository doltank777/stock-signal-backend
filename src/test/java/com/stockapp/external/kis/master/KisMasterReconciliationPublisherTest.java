package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.InstrumentType;
import com.stockapp.domain.stock.KisMasterSyncExecution;
import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.KisMasterSyncExecutionStatus;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMasterStatusEventType;
import com.stockapp.domain.stock.StockMasterStatusHistoryRepository;
import com.stockapp.domain.stock.StockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({KisMasterReconciliationPublisher.class,
        KisMasterReconciliationPublisherTest.ClockConfiguration.class})
class KisMasterReconciliationPublisherTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T01:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-09-01T01:01:00Z");

    @Autowired
    KisMasterReconciliationPublisher publisher;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    KisMasterSyncExecutionRepository executionRepository;

    @Autowired
    StockMasterStatusHistoryRepository historyRepository;

    @Test
    void initializesPresentAndMissingLegacyBaselinesWithOneEventEach() {
        Stock present = legacy("000001", MarketType.KOSPI);
        Stock missing = legacy("000002", MarketType.KOSPI);
        KisMasterSyncExecution execution = runningExecution();

        KisMasterReconciliationResult result = publisher.publish(
                snapshot(MarketType.KOSPI, readyRecord("000001", "KR7000000001",
                        "present", MarketType.KOSPI)),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("100001", "KR7100000001",
                        MarketType.KOSDAQ)),
                execution.getId());

        Stock refreshedPresent = stockRepository.findById(present.getId()).orElseThrow();
        Stock refreshedMissing = stockRepository.findById(missing.getId()).orElseThrow();
        assertThat(refreshedPresent.getPresentInLatestMaster()).isTrue();
        assertThat(refreshedMissing.getPresentInLatestMaster()).isFalse();
        assertThat(historyTypes(execution)).containsExactlyInAnyOrder(
                StockMasterStatusEventType.MASTER_FIRST_SEEN,
                StockMasterStatusEventType.MASTER_MISSING_OBSERVED);
        assertThat(result.updatedStockCount()).isEqualTo(1);
        assertThat(result.missingStockCount()).isEqualTo(1);
        assertThat(executionRepository.findById(execution.getId()).orElseThrow().getStatus())
                .isEqualTo(KisMasterSyncExecutionStatus.COMPLETED);
    }

    @Test
    void insertsSupportedEvenWhenSuspendedButDoesNotInsertUnsupported() {
        KisMasterSyncExecution execution = runningExecution();
        KisMasterNormalizedRecord suspended = record(
                "000010", "KR7000010000", "suspended", MarketType.KOSPI,
                InstrumentType.COMMON_STOCK, true, true, false);

        KisMasterReconciliationResult result = publisher.publish(
                snapshot(MarketType.KOSPI, suspended),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200000", "KR7200000000",
                        MarketType.KOSDAQ)),
                execution.getId());

        Stock inserted = stockRepository.findByStockCode("000010").orElseThrow();
        assertThat(inserted.getSuspended()).isTrue();
        assertThat(stockRepository.findByStockCode("200000")).isEmpty();
        assertThat(result.newStockCount()).isEqualTo(1);
        assertThat(result.unsupportedMasterCount()).isEqualTo(1);
    }

    @Test
    void appendsOnlyChangedFieldEventsAfterBaseline() {
        KisMasterSyncExecution baselineExecution = runningExecution();
        publisher.publish(
                snapshot(MarketType.KOSPI, readyRecord("000020", "KR7000020000",
                        "old-name", MarketType.KOSPI)),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200001", "KR7200001000",
                        MarketType.KOSDAQ)),
                baselineExecution.getId());
        KisMasterSyncExecution changeExecution = runningExecution();
        KisMasterNormalizedRecord changed = record(
                "000020", "KR7000020001", "new-name", MarketType.KOSPI,
                InstrumentType.COMMON_STOCK, true, true, true);

        publisher.publish(
                snapshot(MarketType.KOSPI, changed),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200001", "KR7200001000",
                        MarketType.KOSDAQ)),
                changeExecution.getId());

        assertThat(historyTypes(changeExecution)).containsExactlyInAnyOrder(
                StockMasterStatusEventType.STOCK_NAME_CHANGED,
                StockMasterStatusEventType.STANDARD_CODE_CHANGED,
                StockMasterStatusEventType.SUSPENDED_CHANGED,
                StockMasterStatusEventType.LIQUIDATION_TRADING_CHANGED);
        assertThat(historyRepository
                .findByMasterSyncExecutionIdOrderByIdAsc(changeExecution.getId()))
                .allSatisfy(history -> assertThat(history.getEffectiveAt()).isNull());
    }

    @Test
    void recordsMissingThenReappearedAndRefreshesCurrentState() {
        Stock stock = observedStock("000030", "KR7000030000", false);
        KisMasterSyncExecution missingExecution = runningExecution();
        publisher.publish(
                snapshot(MarketType.KOSPI, readyRecord("000031", "KR7000031000",
                        "other", MarketType.KOSPI)),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200002", "KR7200002000",
                        MarketType.KOSDAQ)),
                missingExecution.getId());
        assertThat(stockRepository.findById(stock.getId()).orElseThrow()
                .getPresentInLatestMaster()).isFalse();
        assertThat(historyTypes(missingExecution))
                .contains(StockMasterStatusEventType.MASTER_MISSING_OBSERVED);

        KisMasterSyncExecution reappearedExecution = runningExecution();
        publisher.publish(
                snapshot(MarketType.KOSPI, readyRecord("000030", "KR7000030000",
                        "returned", MarketType.KOSPI)),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200002", "KR7200002000",
                        MarketType.KOSDAQ)),
                reappearedExecution.getId());

        Stock refreshed = stockRepository.findById(stock.getId()).orElseThrow();
        assertThat(refreshed.getPresentInLatestMaster()).isTrue();
        assertThat(refreshed.getStockName()).isEqualTo("returned");
        assertThat(historyTypes(reappearedExecution)).contains(
                StockMasterStatusEventType.MASTER_REAPPEARED,
                StockMasterStatusEventType.STOCK_NAME_CHANGED);
    }

    @Test
    void sameStateReconciliationCreatesNoDuplicateHistory() {
        KisMasterSnapshot kospi = snapshot(MarketType.KOSPI,
                readyRecord("000040", "KR7000040000", "same", MarketType.KOSPI));
        KisMasterSnapshot kosdaq = snapshot(MarketType.KOSDAQ,
                unsupportedRecord("200003", "KR7200003000", MarketType.KOSDAQ));
        KisMasterSyncExecution first = runningExecution();
        publisher.publish(kospi, kosdaq, first.getId());
        long historyCount = historyRepository.count();
        KisMasterSyncExecution second = runningExecution();

        KisMasterReconciliationResult result = publisher.publish(
                kospi, kosdaq, second.getId());

        assertThat(stockRepository.findAll()).extracting(Stock::getStockCode)
                .containsExactly("000040");
        assertThat(historyRepository.count()).isEqualTo(historyCount);
        assertThat(result.unchangedStockCount()).isEqualTo(1);
        assertThat(result.historyCreatedCount()).isZero();
    }

    @Test
    void rejectsNewStockCodeWhenStandardCodeBelongsToExistingStock() {
        observedStock("000050", "KR7000050000", false);
        KisMasterSyncExecution execution = runningExecution();

        assertThatThrownBy(() -> publisher.publish(
                snapshot(MarketType.KOSPI, readyRecord("000051", "KR7000050000",
                        "conflict", MarketType.KOSPI)),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200004", "KR7200004000",
                        MarketType.KOSDAQ)),
                execution.getId()))
                .isInstanceOf(KisMasterIdentityConflictException.class);

        assertThat(stockRepository.findByStockCode("000051")).isEmpty();
        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void rejectsNotReadySnapshotBeforeAnyStockMutation() {
        Stock stock = legacy("000060", MarketType.KOSPI);
        KisMasterSyncExecution execution = runningExecution();
        KisMasterSnapshot notReady = snapshot(
                MarketType.KOSPI,
                KisMasterSnapshotValidationStatus.NOT_READY,
                readyRecord("000060", "KR7000060000", "not-ready", MarketType.KOSPI));

        assertThatThrownBy(() -> publisher.publish(
                notReady,
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200005", "KR7200005000",
                        MarketType.KOSDAQ)),
                execution.getId()))
                .isInstanceOf(KisMasterSnapshotNotReadyException.class);

        assertThat(stockRepository.findById(stock.getId()).orElseThrow()
                .getPresentInLatestMaster()).isNull();
        assertThat(historyRepository.count()).isZero();
    }

    @Test
    void rejectsPublishWhenAnotherExecutionIsRunning() {
        KisMasterSyncExecution first = runningExecution();
        KisMasterSyncExecution second = runningExecution();

        assertThatThrownBy(() -> publisher.publish(
                snapshot(MarketType.KOSPI, readyRecord("000070", "KR7000070000",
                        "concurrent", MarketType.KOSPI)),
                snapshot(MarketType.KOSDAQ, unsupportedRecord("200006", "KR7200006000",
                        MarketType.KOSDAQ)),
                second.getId()))
                .isInstanceOf(KisMasterConcurrentExecutionException.class);

        assertThat(executionRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(KisMasterSyncExecutionStatus.RUNNING);
        assertThat(stockRepository.findByStockCode("000070")).isEmpty();
    }

    private Stock legacy(String code, MarketType market) {
        return stockRepository.save(Stock.builder()
                .stockCode(code)
                .stockName("legacy-" + code)
                .marketType(market)
                .build());
    }

    private Stock observedStock(String code, String standardCode, boolean suspended) {
        KisMasterSyncExecution execution = runningExecution();
        execution.complete(completion(), FINISHED_AT);
        return stockRepository.save(Stock.builder()
                .stockCode(code)
                .stockName("observed-" + code)
                .marketType(MarketType.KOSPI)
                .standardCode(standardCode)
                .instrumentType(InstrumentType.COMMON_STOCK)
                .securityGroupCode("ST")
                .preferredStockCode("0")
                .etpProductCode("")
                .listingDate(LocalDate.of(2026, 1, 1))
                .spac(false)
                .suspended(suspended)
                .liquidationTrading(false)
                .managedIssue(false)
                .presentInLatestMaster(true)
                .masterObservedAt(OBSERVED_AT.minusSeconds(60))
                .masterSyncExecution(execution)
                .build());
    }

    private KisMasterSyncExecution runningExecution() {
        return executionRepository.saveAndFlush(
                KisMasterSyncExecution.create(OBSERVED_AT.minusSeconds(10)));
    }

    private com.stockapp.domain.stock.KisMasterSyncExecutionCompletion completion() {
        return new com.stockapp.domain.stock.KisMasterSyncExecutionCompletion(
                OBSERVED_AT.minusSeconds(60), 1, 1, 1, 1, 0, 0, 0);
    }

    private List<StockMasterStatusEventType> historyTypes(KisMasterSyncExecution execution) {
        return historyRepository.findByMasterSyncExecutionIdOrderByIdAsc(execution.getId())
                .stream()
                .map(history -> history.getEventType())
                .toList();
    }

    private KisMasterNormalizedRecord readyRecord(
            String code, String standardCode, String name, MarketType market
    ) {
        return record(code, standardCode, name, market,
                InstrumentType.COMMON_STOCK, true, false, false);
    }

    private KisMasterNormalizedRecord unsupportedRecord(
            String code, String standardCode, MarketType market
    ) {
        return record(code, standardCode, "ETF-" + code, market,
                InstrumentType.ETF, false, false, false);
    }

    private KisMasterNormalizedRecord record(
            String code,
            String standardCode,
            String name,
            MarketType market,
            InstrumentType type,
            boolean supported,
            boolean suspended,
            boolean liquidation
    ) {
        String group = type == InstrumentType.ETF ? "EF" : "ST";
        KisMasterRawRecord raw = new KisMasterRawRecord(
                market, code, standardCode, name, group, "0", "",
                false, suspended, liquidation, false,
                LocalDate.of(2026, 1, 1), "", List.of());
        return new KisMasterNormalizedRecord(
                market, code, standardCode, name, LocalDate.of(2026, 1, 1),
                type, supported, group, "0", "", false, suspended,
                liquidation, false, raw);
    }

    private KisMasterSnapshot snapshot(
            MarketType market,
            KisMasterNormalizedRecord... records
    ) {
        return snapshot(market, KisMasterSnapshotValidationStatus.READY, records);
    }

    private KisMasterSnapshot snapshot(
            MarketType market,
            KisMasterSnapshotValidationStatus status,
            KisMasterNormalizedRecord... records
    ) {
        int supported = (int) List.of(records).stream()
                .filter(KisMasterNormalizedRecord::instrumentSupported)
                .count();
        KisMasterSnapshotValidationResult validation =
                new KisMasterSnapshotValidationResult(
                        status,
                        status == KisMasterSnapshotValidationStatus.READY
                                ? List.of() : List.of("not ready"),
                        List.of(), records.length, records.length,
                        supported, records.length - supported,
                        0, 0, 0, Set.of());
        return new KisMasterSnapshot(
                market, OBSERVED_AT, List.of(records), records.length,
                records.length, supported, records.length - supported, validation);
    }

    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(FINISHED_AT, ZoneOffset.UTC);
        }
    }
}
