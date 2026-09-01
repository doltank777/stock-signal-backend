package com.stockbatch.kismasterreconciliation;

import com.stockapp.domain.stock.KisMasterSyncExecution;
import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.external.kis.master.KisMasterClient;
import com.stockapp.external.kis.master.KisMasterReconciliationPlan;
import com.stockapp.external.kis.master.KisMasterReconciliationPlanner;
import com.stockapp.external.kis.master.KisMasterReconciliationResult;
import com.stockapp.external.kis.master.KisMasterReconciliationService;
import com.stockapp.external.kis.master.KisMasterSnapshot;
import com.stockapp.external.kis.master.KisMasterSnapshotFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class KisMasterReconciliationRunner implements ApplicationRunner {

    private final KisMasterReconciliationProperties properties;
    private final KisMasterClient client;
    private final KisMasterSnapshotFactory snapshotFactory;
    private final KisMasterReconciliationPlanner planner;
    private final KisMasterSyncExecutionRepository executionRepository;
    private final KisMasterReconciliationService reconciliationService;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        execute();
    }

    public Optional<KisMasterReconciliationResult> execute() {
        Optional<KisMasterReconciliationMode> mode =
                KisMasterReconciliationMode.parse(properties.mode());
        if (mode.isEmpty()) {
            log.error("KIS Master reconciliation refused: explicit mode must be dry-run or apply");
            return Optional.empty();
        }

        KisMasterSnapshot kospi = snapshot(MarketType.KOSPI);
        KisMasterSnapshot kosdaq = snapshot(MarketType.KOSDAQ);
        KisMasterReconciliationPlan plan = planner.plan(kospi, kosdaq);
        logPlan(mode.get(), plan);
        if (mode.get() == KisMasterReconciliationMode.DRY_RUN) {
            return Optional.empty();
        }
        if (!plan.applyAllowed()) {
            throw new IllegalStateException(
                    "KIS Master APPLY blocked: ready=" + plan.ready()
                            + ", identityConflicts=" + plan.identityConflicts().size()
                            + ", runningExecutions=" + plan.runningExecutionCount());
        }

        KisMasterSyncExecution execution = executionRepository.saveAndFlush(
                KisMasterSyncExecution.create(Instant.now(clock)));
        KisMasterReconciliationResult result = reconciliationService.reconcile(
                kospi, kosdaq, execution);
        KisMasterSyncExecution completed = executionRepository
                .findById(execution.getId()).orElseThrow();
        log.info("[KIS MASTER APPLY RESULT] executionId={} status={} observedAt={} result={}",
                completed.getId(), completed.getStatus(), completed.getObservedAt(), result);
        return Optional.of(result);
    }

    private KisMasterSnapshot snapshot(MarketType market) {
        return snapshotFactory.create(market, client.downloadAndParse(market));
    }

    private void logPlan(
            KisMasterReconciliationMode mode,
            KisMasterReconciliationPlan plan
    ) {
        log.info("[KIS MASTER RECONCILIATION PLAN] mode={} ready={}/{} "
                        + "masterTotal={} supported={} unsupported={} targetStocks={} "
                        + "matched={} existingUnsupported={} newSupported={} missing={} "
                        + "estimatedUpdated={} unchanged={} reappeared={} conflicts={} "
                        + "runningExecutions={}",
                mode, plan.kospiReady(), plan.kosdaqReady(),
                plan.totalMasterRecords(), plan.supportedMasterRecords(),
                plan.unsupportedMasterRecords(), plan.targetStockCount(),
                plan.existingMatchedCount(), plan.existingUnsupportedCount(),
                plan.newSupportedStocks().size(), plan.missingStocks().size(),
                plan.estimatedUpdatedStockCount(), plan.unchangedStockCount(),
                plan.reappearedStockCount(), plan.identityConflicts().size(),
                plan.runningExecutionCount());
        plan.newSupportedStocks().forEach(candidate ->
                log.info("[NEW_SUPPORTED] {}", candidate));
        plan.missingStocks().forEach(missing ->
                log.info("[NOT_PRESENT_IN_LATEST_MASTER] {}", missing));
        plan.identityConflicts().forEach(conflict ->
                log.error("[IDENTITY_CONFLICT] {}", conflict));
    }
}
