package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.KisMasterSyncExecution;
import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.KisMasterSyncExecutionStatus;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMasterStatusHistoryRepository;
import com.stockapp.domain.stock.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({KisMasterReconciliationService.class,
        KisMasterReconciliationPublisher.class,
        KisMasterSyncExecutionFailureRecorder.class,
        KisMasterReconciliationLifecyclePersistenceTest.ClockConfiguration.class})
class KisMasterReconciliationLifecyclePersistenceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T02:00:00Z");

    @Autowired
    KisMasterReconciliationService service;

    @Autowired
    StockRepository stockRepository;

    @Autowired
    KisMasterSyncExecutionRepository executionRepository;

    @Autowired
    StockMasterStatusHistoryRepository historyRepository;

    @AfterEach
    void cleanDatabase() {
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        executionRepository.deleteAll();
    }

    @Test
    void notReadyFailureLeavesStocksUntouchedAndPersistsFailedExecution() {
        Stock stock = stockRepository.save(Stock.builder()
                .stockCode("000080")
                .stockName("legacy")
                .marketType(MarketType.KOSPI)
                .build());
        KisMasterSyncExecution execution = executionRepository.save(
                KisMasterSyncExecution.create(NOW.minusSeconds(10)));

        assertThatThrownBy(() -> service.reconcile(
                snapshot(MarketType.KOSPI, false),
                snapshot(MarketType.KOSDAQ, true),
                execution))
                .isInstanceOf(KisMasterSnapshotNotReadyException.class);

        Stock unchanged = stockRepository.findById(stock.getId()).orElseThrow();
        KisMasterSyncExecution failed = executionRepository
                .findById(execution.getId()).orElseThrow();
        assertThat(unchanged.getPresentInLatestMaster()).isNull();
        assertThat(historyRepository.count()).isZero();
        assertThat(failed.getStatus()).isEqualTo(KisMasterSyncExecutionStatus.FAILED);
        assertThat(failed.getLastError()).contains("must be READY");
    }

    private KisMasterSnapshot snapshot(MarketType market, boolean ready) {
        KisMasterSnapshotValidationResult validation =
                new KisMasterSnapshotValidationResult(
                        ready ? KisMasterSnapshotValidationStatus.READY
                                : KisMasterSnapshotValidationStatus.NOT_READY,
                        ready ? List.of() : List.of("not ready"),
                        List.of(), 0, 0, 0, 0, 0, 0, 0, Set.of());
        return new KisMasterSnapshot(
                market, NOW, List.of(), 0, 0, 0, 0, validation);
    }

    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
