package com.stockapp.domain.stock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class KisMasterPersistenceRepositoryTest {

    @Autowired
    StockRepository stockRepository;

    @Autowired
    KisMasterSyncExecutionRepository executionRepository;

    @Autowired
    StockMasterStatusHistoryRepository historyRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void persistsCompletedExecutionAndEnumAsString() {
        KisMasterSyncExecution execution = completedExecution();

        KisMasterSyncExecution saved = executionRepository.saveAndFlush(execution);

        assertThat(executionRepository
                .findFirstByStatusOrderByFinishedAtDescIdDesc(
                        KisMasterSyncExecutionStatus.COMPLETED))
                .get().extracting(KisMasterSyncExecution::getId)
                .isEqualTo(saved.getId());
        assertThat(jdbcTemplate.queryForObject(
                "select status from kis_master_sync_executions where id = ?",
                String.class,
                saved.getId())).isEqualTo("COMPLETED");
    }

    @Test
    void persistsNullableLegacyStockAndFullyObservedMasterState() {
        Stock legacy = stockRepository.saveAndFlush(Stock.builder()
                .stockCode("000001")
                .stockName("기존엑셀종목")
                .marketType(MarketType.KOSPI)
                .build());

        assertThat(legacy.getPresentInLatestMaster()).isNull();
        assertThat(legacy.getInstrumentType()).isNull();
        assertThat(legacy.getSuspended()).isNull();

        KisMasterSyncExecution execution = executionRepository.saveAndFlush(
                completedExecution());
        Stock observed = stockRepository.saveAndFlush(Stock.builder()
                .stockCode("0220W0")
                .stockName("한화머시너리앤서비스홀딩스")
                .marketType(MarketType.KOSPI)
                .standardCode("KR70220W0000")
                .instrumentType(InstrumentType.COMMON_STOCK)
                .securityGroupCode("ST")
                .preferredStockCode("0")
                .etpProductCode("")
                .listingDate(LocalDate.of(2026, 8, 25))
                .spac(false)
                .suspended(false)
                .liquidationTrading(false)
                .managedIssue(false)
                .presentInLatestMaster(true)
                .masterObservedAt(Instant.parse("2026-09-01T00:00:05Z"))
                .masterSyncExecution(execution)
                .build());

        assertThat(observed.getInstrumentType()).isEqualTo(InstrumentType.COMMON_STOCK);
        assertThat(observed.getMasterSyncExecution().getId()).isEqualTo(execution.getId());
        assertThat(jdbcTemplate.queryForObject(
                "select instrument_type from stocks where id = ?",
                String.class,
                observed.getId())).isEqualTo("COMMON_STOCK");
    }

    @Test
    void persistsAppendOnlyHistoryWithNullableEffectiveAtAndForeignKeys() {
        KisMasterSyncExecution execution = executionRepository.saveAndFlush(
                completedExecution());
        Stock stock = stockRepository.saveAndFlush(Stock.builder()
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build());
        Instant observedAt = Instant.parse("2026-09-01T00:00:05Z");
        StockMasterStatusHistory history = StockMasterStatusHistory.create(
                stock,
                StockMasterStatusEventType.SUSPENDED_CHANGED,
                "false",
                "true",
                observedAt,
                null,
                execution);

        StockMasterStatusHistory saved = historyRepository.saveAndFlush(history);

        assertThat(saved.getEffectiveAt()).isNull();
        assertThat(saved.getStock().getId()).isEqualTo(stock.getId());
        assertThat(saved.getMasterSyncExecution().getId()).isEqualTo(execution.getId());
        assertThat(historyRepository.findByStockIdOrderByObservedAtDescIdDesc(
                stock.getId())).extracting(StockMasterStatusHistory::getId)
                .containsExactly(saved.getId());
        assertThat(historyRepository
                .findByMasterSyncExecutionIdOrderByIdAsc(execution.getId()))
                .extracting(StockMasterStatusHistory::getId)
                .containsExactly(saved.getId());
        assertThat(jdbcTemplate.queryForObject(
                "select event_type from stock_master_status_histories where id = ?",
                String.class,
                saved.getId())).isEqualTo("SUSPENDED_CHANGED");
    }

    private KisMasterSyncExecution completedExecution() {
        KisMasterSyncExecution execution = KisMasterSyncExecution.create(
                Instant.parse("2026-09-01T00:00:00Z"));
        execution.complete(new KisMasterSyncExecutionCompletion(
                        Instant.parse("2026-09-01T00:00:05Z"),
                        2571, 1824, 2654, 1741, 0, 0, 0),
                Instant.parse("2026-09-01T00:00:10Z"));
        return execution;
    }
}
