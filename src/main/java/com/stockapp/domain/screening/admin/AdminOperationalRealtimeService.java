package com.stockapp.domain.screening.admin;

import com.stockapp.domain.screening.LatestScreeningSnapshot;
import com.stockapp.domain.screening.LatestScreeningSnapshotRegistry;
import com.stockapp.domain.screening.admin.dto.AdminOperationalRealtimeRecoveryResponse;
import com.stockapp.domain.screening.admin.dto.AdminOperationalRealtimeStatusResponse;
import com.stockapp.domain.screening.realtime.*;
import com.stockapp.external.kis.ManagedRealtimeSubscriptionGateway;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;

@Service
public class AdminOperationalRealtimeService {

    private final OperationalMorningRunCoordinator coordinator;
    private final LatestScreeningSnapshotRegistry screeningRegistry;
    private final LatestOperationalRealtimeSelectionRegistry selectionRegistry;
    private final RealtimeWatchTargetRegistry targetRegistry;
    private final ManagedRealtimeSubscriptionGateway gateway;
    private final OperationalRealtimeAutomationProperties properties;
    private final KrxRegularMarketSessionPolicy sessionPolicy;
    private final Environment environment;

    public AdminOperationalRealtimeService(
            OperationalMorningRunCoordinator coordinator,
            LatestScreeningSnapshotRegistry screeningRegistry,
            LatestOperationalRealtimeSelectionRegistry selectionRegistry,
            RealtimeWatchTargetRegistry targetRegistry,
            ManagedRealtimeSubscriptionGateway gateway,
            OperationalRealtimeAutomationProperties properties,
            KrxRegularMarketSessionPolicy sessionPolicy,
            Environment environment) {
        this.coordinator = coordinator;
        this.screeningRegistry = screeningRegistry;
        this.selectionRegistry = selectionRegistry;
        this.targetRegistry = targetRegistry;
        this.gateway = gateway;
        this.properties = properties;
        this.sessionPolicy = sessionPolicy;
        this.environment = environment;
    }

    public AdminOperationalRealtimeStatusResponse getStatus() {
        ZonedDateTime now = sessionPolicy.now();
        OperationalMorningRunSnapshot morning = coordinator.snapshot();
        Optional<LatestScreeningSnapshot> screening = screeningRegistry.findLatest();
        Optional<OperationalRealtimeTargetSelection> desired = selectionRegistry.findLatest();
        Map<String, RealtimeWatchTarget> registry = targetRegistry.findAll();
        Set<String> physical = gateway.currentActiveStockCodes();
        Set<String> desiredCodes = desired.map(value -> codes(value.selectedTargets()))
                .orElseGet(Set::of);
        Set<String> registryCodes = Set.copyOf(registry.keySet());
        List<String> unmappedPhysical = difference(physical, registryCodes);
        RealtimeTargetReconciliationResult failure = morning.lastReconciliation()
                .filter(value -> value.failureType() != null).orElse(null);

        return new AdminOperationalRealtimeStatusResponse(
                now,
                new AdminOperationalRealtimeStatusResponse.Session(
                        !now.toLocalTime().isBefore(properties.getMorningStart())
                                && !now.toLocalTime().isAfter(properties.getMarketClose()),
                        sessionPolicy.isMorningPreparationWindow(now),
                        sessionPolicy.isRegularMonitoringWindow(now),
                        sessionPolicy.isMorningDeadlineReached(now)),
                new AdminOperationalRealtimeStatusResponse.Automation(
                        environment.getProperty(
                                "operational-screening.realtime.morning.enabled",
                                Boolean.class, false),
                        properties.getMorningStart(), properties.getMorningDeadline(),
                        properties.getRetryInterval(), properties.getMarketOpen(),
                        properties.getMarketClose()),
                new AdminOperationalRealtimeStatusResponse.Morning(
                        morning.date(), morning.status(), morning.attemptCount(),
                        morning.lastAttemptAt().orElse(null),
                        morning.pendingSelection().isPresent(),
                        morning.pendingSelection().map(
                                OperationalRealtimeTargetSelection::selectedCount).orElse(0),
                        morning.staleClearResult().map(value -> value.status().name()).orElse(null),
                        failure == null || failure.failedOperation() == null ? null
                                : failure.failedOperation().name(),
                        failure == null ? null : failure.failedStockCode(),
                        failure == null ? null : failure.failureMessage()),
                screening.map(value -> new AdminOperationalRealtimeStatusResponse.Screening(
                                true, value.baseDate(), value.candidates().size(),
                                !value.baseDate().equals(now.toLocalDate())))
                        .orElseGet(() -> new AdminOperationalRealtimeStatusResponse.Screening(
                                false, null, 0, false)),
                desired.map(this::desiredResponse).orElseGet(() ->
                        new AdminOperationalRealtimeStatusResponse.Desired(
                                false, RealtimeWatchPolicy.CAPACITY, 0, 0, 0, List.of())),
                new AdminOperationalRealtimeStatusResponse.Applied(
                        RealtimeWatchPolicy.CAPACITY, registryCodes.size(), physical.size(),
                        unmappedPhysical.size(), !registryCodes.equals(physical),
                        desired.isPresent() && desiredCodes.equals(registryCodes)
                                && registryCodes.equals(physical),
                        difference(desiredCodes, registryCodes),
                        difference(registryCodes, desiredCodes), unmappedPhysical));
    }

    public AdminOperationalRealtimeRecoveryResponse retryPendingReconciliation() {
        OperationalMorningManualRetryResult result =
                coordinator.retryPendingReconciliationNow();
        RealtimeTargetReconciliationResult reconciliation =
                result.reconciliation().orElse(null);
        return new AdminOperationalRealtimeRecoveryResponse(
                result.status(), reconciliation == null ? null : reconciliation.status(),
                reconciliation == null ? null : reconciliation.desiredCount(),
                reconciliation == null ? null : reconciliation.afterPhysicalCount(),
                reconciliation == null ? null : reconciliation.registryCount(),
                reconciliation == null || reconciliation.failedOperation() == null ? null
                        : reconciliation.failedOperation().name(),
                reconciliation == null ? null : reconciliation.failedStockCode(),
                reconciliation == null ? null : reconciliation.failureMessage());
    }

    private AdminOperationalRealtimeStatusResponse.Desired desiredResponse(
            OperationalRealtimeTargetSelection selection) {
        List<AdminOperationalRealtimeStatusResponse.Target> targets = new ArrayList<>();
        for (int index = 0; index < selection.selectedTargets().size(); index++) {
            DesiredRealtimeTarget target = selection.selectedTargets().get(index);
            targets.add(new AdminOperationalRealtimeStatusResponse.Target(
                    index + 1, target.stockId(), target.stockCode(), target.stockName(),
                    target.market(), target.effectivePriority(),
                    target.effectiveScreeningScore(), target.matchedConditions().stream()
                    .map(condition -> new AdminOperationalRealtimeStatusResponse.Condition(
                            condition.searchConditionId(), condition.searchConditionName(),
                            condition.priority(), condition.screeningScore())).toList()));
        }
        return new AdminOperationalRealtimeStatusResponse.Desired(
                true, selection.capacity(), selection.uniqueCandidateCount(),
                selection.selectedCount(), selection.excludedCount(), List.copyOf(targets));
    }

    private static Set<String> codes(List<DesiredRealtimeTarget> targets) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        targets.forEach(target -> values.add(target.stockCode()));
        return Set.copyOf(values);
    }

    private static List<String> difference(Set<String> left, Set<String> right) {
        return left.stream().filter(value -> !right.contains(value)).sorted().toList();
    }
}
