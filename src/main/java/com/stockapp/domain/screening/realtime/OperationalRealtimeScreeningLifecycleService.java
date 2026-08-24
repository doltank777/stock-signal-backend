package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.LatestScreeningSnapshotRegistry;
import com.stockapp.domain.screening.OperationalScreeningRunResult;
import com.stockapp.domain.screening.OperationalScreeningRunService;
import com.stockapp.domain.screening.OperationalScreeningRunStatus;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationalRealtimeScreeningLifecycleService {

    private final OperationalScreeningRunService screeningRunService;
    private final LatestScreeningSnapshotRegistry screeningSnapshotRegistry;
    private final OperationalRealtimeTargetSelector targetSelector;
    private final RealtimeTargetReconciliationService reconciliationService;

    public OperationalRealtimeScreeningLifecycleResult run() {
        OperationalScreeningRunResult screeningRun =
                screeningRunService.run();
        if (screeningRun.status()
                != OperationalScreeningRunStatus.COMPLETED) {
            return OperationalRealtimeScreeningLifecycleResult.skipped(
                    screeningRun);
        }

        ScreeningRunResult screeningResult = screeningRun.screeningResult()
                .orElseThrow(() -> new IllegalStateException(
                        "completed operational screening result is missing"));
        screeningSnapshotRegistry.replace(screeningResult);
        OperationalRealtimeTargetSelection selection =
                targetSelector.select(screeningResult);
        RealtimeTargetReconciliationResult reconciliation =
                reconciliationService.reconcile(selection);
        return OperationalRealtimeScreeningLifecycleResult.completed(
                screeningRun, selection, reconciliation);
    }
}
