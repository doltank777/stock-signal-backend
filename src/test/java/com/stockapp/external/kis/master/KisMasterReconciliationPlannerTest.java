package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.InstrumentType;
import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMasterStatusHistoryRepository;
import com.stockapp.domain.stock.StockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(KisMasterReconciliationPlanner.class)
class KisMasterReconciliationPlannerTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T03:00:00Z");

    @Autowired
    KisMasterReconciliationPlanner planner;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    KisMasterSyncExecutionRepository executionRepository;

    @Autowired
    StockMasterStatusHistoryRepository historyRepository;

    @Test
    void plansWithoutWritesAndProtectsNonTargetMarket() {
        Stock matched = stockRepository.save(Stock.builder()
                .stockCode("000001").stockName("legacy")
                .marketType(MarketType.KOSPI).build());
        Stock missing = stockRepository.save(Stock.builder()
                .stockCode("000002").stockName("missing")
                .marketType(MarketType.KOSDAQ).build());
        Stock conflict = stockRepository.save(Stock.builder()
                .stockCode("000003").stockName("conflict-owner")
                .marketType(MarketType.KOSPI).standardCode("KR7000099999").build());
        Stock konex = stockRepository.save(Stock.builder()
                .stockCode("900001").stockName("konex")
                .marketType(MarketType.KONEX).build());
        long stocksBefore = stockRepository.count();
        long executionsBefore = executionRepository.count();
        long historiesBefore = historyRepository.count();

        KisMasterReconciliationPlan plan = planner.plan(
                snapshot(MarketType.KOSPI,
                        record("000001", "KR7000000001", MarketType.KOSPI,
                                InstrumentType.COMMON_STOCK, true),
                        record("000010", "KR7000010000", MarketType.KOSPI,
                                InstrumentType.COMMON_STOCK, true),
                        record("000011", "KR7000099999", MarketType.KOSPI,
                                InstrumentType.COMMON_STOCK, true)),
                snapshot(MarketType.KOSDAQ,
                        record("200000", "KR7200000000", MarketType.KOSDAQ,
                                InstrumentType.ETF, false)));

        assertThat(plan.ready()).isTrue();
        assertThat(plan.targetStockCount()).isEqualTo(3);
        assertThat(plan.existingMatchedCount()).isEqualTo(1);
        assertThat(plan.newSupportedStocks()).extracting(
                KisMasterReconciliationCandidate::stockCode)
                .containsExactly("000010");
        assertThat(plan.missingStocks()).extracting(KisMasterMissingStock::stockCode)
                .containsExactlyInAnyOrder(missing.getStockCode(), conflict.getStockCode());
        assertThat(plan.identityConflicts()).singleElement().satisfies(identity -> {
            assertThat(identity.masterStockCode()).isEqualTo("000011");
            assertThat(identity.existingStockCode()).isEqualTo("000003");
        });
        assertThat(plan.applyAllowed()).isFalse();
        assertThat(stockRepository.count()).isEqualTo(stocksBefore);
        assertThat(executionRepository.count()).isEqualTo(executionsBefore);
        assertThat(historyRepository.count()).isEqualTo(historiesBefore);
        assertThat(stockRepository.findById(konex.getId()).orElseThrow()
                .getPresentInLatestMaster()).isNull();
        assertThat(stockRepository.findById(matched.getId()).orElseThrow()
                .getPresentInLatestMaster()).isNull();
    }

    @Test
    void notReadySnapshotStillProducesReadOnlyPlanButBlocksApply() {
        KisMasterSnapshot kospi = snapshot(MarketType.KOSPI,
                KisMasterSnapshotValidationStatus.NOT_READY,
                record("000020", "KR7000020000", MarketType.KOSPI,
                        InstrumentType.COMMON_STOCK, true));

        KisMasterReconciliationPlan plan = planner.plan(
                kospi,
                snapshot(MarketType.KOSDAQ,
                        record("200001", "KR7200001000", MarketType.KOSDAQ,
                                InstrumentType.ETF, false)));

        assertThat(plan.ready()).isFalse();
        assertThat(plan.applyAllowed()).isFalse();
        assertThat(stockRepository.count()).isZero();
        assertThat(executionRepository.count()).isZero();
        assertThat(historyRepository.count()).isZero();
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
                .filter(KisMasterNormalizedRecord::instrumentSupported).count();
        KisMasterSnapshotValidationResult validation =
                new KisMasterSnapshotValidationResult(
                        status, status == KisMasterSnapshotValidationStatus.READY
                        ? List.of() : List.of("not ready"), List.of(),
                        records.length, records.length, supported,
                        records.length - supported, 0, 0, 0, Set.of());
        return new KisMasterSnapshot(
                market, OBSERVED_AT, List.of(records), records.length,
                records.length, supported, records.length - supported, validation);
    }

    private KisMasterNormalizedRecord record(
            String code,
            String standardCode,
            MarketType market,
            InstrumentType type,
            boolean supported
    ) {
        String group = type == InstrumentType.ETF ? "EF" : "ST";
        KisMasterRawRecord raw = new KisMasterRawRecord(
                market, code, standardCode, "name-" + code, group, "0", "",
                false, false, false, false, LocalDate.of(2026, 1, 1), "", List.of());
        return new KisMasterNormalizedRecord(
                market, code, standardCode, "name-" + code,
                LocalDate.of(2026, 1, 1), type, supported, group, "0", "",
                false, false, false, false, raw);
    }
}
