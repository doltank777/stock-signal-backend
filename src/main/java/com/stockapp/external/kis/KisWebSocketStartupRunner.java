package com.stockapp.external.kis;

import com.stockapp.domain.screening.realtime.OperationalRealtimeScreeningLifecycleResult;
import com.stockapp.domain.screening.realtime.OperationalRealtimeScreeningLifecycleService;
import com.stockapp.domain.screening.realtime.OperationalRealtimeTargetSelection;
import com.stockapp.domain.screening.realtime.OperationalMorningRunCoordinator;
import com.stockapp.domain.screening.realtime.KrxRegularMarketSessionPolicy;
import com.stockapp.domain.screening.realtime.RealtimeTargetReconciliationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Profile("local & !test & !daily-price-load & !daily-price-update & !screening-run & !schema-validate & !kis-websocket-probe")
@RequiredArgsConstructor
public class KisWebSocketStartupRunner implements ApplicationRunner {

    private final OperationalRealtimeScreeningLifecycleService lifecycleService;
    private final KisWebSocketSessionManager sessionManager;
    private final KrxRegularMarketSessionPolicy sessionPolicy;
    private final OperationalMorningRunCoordinator morningCoordinator;

    @Override
    public void run(ApplicationArguments args) {
        if (!sessionPolicy.isStartupRecoveryWindow()) {
            log.info("realtime startup recovery skipped - reason: outside "
                    + "recovery/monitoring window");
            return;
        }
        var preparation = lifecycleService.prepare();
        if (preparation.selection().isEmpty()) {
            log.info("realtime startup recovery skipped - screeningStatus: {}",
                    preparation.screeningRun().status());
            return;
        }

        OperationalRealtimeTargetSelection selection = preparation.selection()
                .orElseThrow();
        List<String> desiredStockCodes = selection.selectedTargets().stream()
                .map(target -> target.stockCode())
                .toList();
        if (!desiredStockCodes.isEmpty()) {
            sessionManager.connectAll(desiredStockCodes);
        }
        OperationalRealtimeScreeningLifecycleResult result =
                lifecycleService.apply(preparation);
        morningCoordinator.recordStartupResult(result);
        var reconciliation = result.reconciliation().orElseThrow();
        if (reconciliation.status()
                == RealtimeTargetReconciliationStatus.PARTIAL_FAILURE) {
            log.warn("realtime startup recovery partially completed - "
                            + "desiredCount: {}, afterPhysicalCount: {}, "
                            + "failedOperation: {}, failedStockCode: {}",
                    selection.selectedCount(),
                    reconciliation.afterPhysicalCount(),
                    reconciliation.failedOperation(),
                    reconciliation.failedStockCode());
            return;
        }
        log.info("realtime startup recovery completed - desiredCount: {}, "
                        + "reconciliationStatus: {}, afterPhysicalCount: {}",
                selection.selectedCount(), reconciliation.status(),
                reconciliation.afterPhysicalCount());
    }
}
