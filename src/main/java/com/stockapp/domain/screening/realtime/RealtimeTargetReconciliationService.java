package com.stockapp.domain.screening.realtime;

import com.stockapp.external.kis.KisWebSocketException;
import com.stockapp.external.kis.ManagedRealtimeSubscriptionGateway;
import com.stockapp.external.kis.RealtimeSubscriptionCapacityExceededException;
import com.stockapp.external.kis.RealtimeSubscriptionCommandOperation;
import com.stockapp.external.kis.RealtimeSubscriptionSessionUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class RealtimeTargetReconciliationService {

    private final RealtimeWatchTargetRegistry targetRegistry;
    private final RealtimeTargetDiffPlanner diffPlanner;
    private final ManagedRealtimeSubscriptionGateway subscriptionGateway;
    private final ReentrantLock reconciliationLock = new ReentrantLock();

    public RealtimeTargetReconciliationService(
            RealtimeWatchTargetRegistry targetRegistry,
            RealtimeTargetDiffPlanner diffPlanner,
            ManagedRealtimeSubscriptionGateway subscriptionGateway
    ) {
        this.targetRegistry = targetRegistry;
        this.diffPlanner = diffPlanner;
        this.subscriptionGateway = subscriptionGateway;
    }

    public RealtimeTargetReconciliationResult reconcile(
            OperationalRealtimeTargetSelection desiredSelection
    ) {
        Objects.requireNonNull(desiredSelection,
                "desiredSelection is required");
        reconciliationLock.lock();
        try {
            return reconcileLocked(desiredSelection);
        } finally {
            reconciliationLock.unlock();
        }
    }

    private RealtimeTargetReconciliationResult reconcileLocked(
            OperationalRealtimeTargetSelection desiredSelection
    ) {
        List<RealtimeWatchTarget> currentRegistryTargets = List.copyOf(
                targetRegistry.findAll().values());
        Set<String> beforePhysical = subscriptionGateway
                .currentActiveStockCodes();
        RealtimeTargetDiff diff = diffPlanner.plan(
                desiredSelection, currentRegistryTargets, beforePhysical);

        if (!diff.requiresPhysicalChanges()
                && !diff.requiresRegistryUpdate()) {
            return result(RealtimeTargetReconciliationStatus.NO_OP, diff,
                    beforePhysical, beforePhysical, List.of(), List.of(),
                    currentRegistryTargets.size(), null, List.of());
        }

        List<String> unsubscribeApplied = new ArrayList<>();
        List<String> subscribeApplied = new ArrayList<>();
        CommandFailure failure = null;
        boolean commandAttempted = false;

        for (String stockCode : diff.toUnsubscribeStockCodes()) {
            commandAttempted = true;
            try {
                subscriptionGateway.unsubscribe(stockCode);
                unsubscribeApplied.add(stockCode);
            } catch (RealtimeSubscriptionSessionUnavailableException
                     | RealtimeSubscriptionCapacityExceededException
                     | KisWebSocketException exception) {
                failure = CommandFailure.of(
                        RealtimeSubscriptionCommandOperation.UNSUBSCRIBE,
                        stockCode, exception);
                break;
            }
        }

        if (failure == null) {
            for (RealtimeWatchTarget target : diff.toSubscribeTargets()) {
                commandAttempted = true;
                try {
                    subscriptionGateway.subscribe(target.stockCode());
                    subscribeApplied.add(target.stockCode());
                } catch (RealtimeSubscriptionSessionUnavailableException
                         | RealtimeSubscriptionCapacityExceededException
                         | KisWebSocketException exception) {
                    failure = CommandFailure.of(
                            RealtimeSubscriptionCommandOperation.SUBSCRIBE,
                            target.stockCode(), exception);
                    break;
                }
            }
        }

        Set<String> afterPhysical = commandAttempted
                ? subscriptionGateway.currentActiveStockCodes()
                : beforePhysical;
        RegistrySnapshot finalSnapshot = finalRegistrySnapshot(
                diff.desiredTargets(), currentRegistryTargets, afterPhysical);
        if (!sameSnapshot(currentRegistryTargets, finalSnapshot.targets())) {
            targetRegistry.replace(finalSnapshot.targets());
        }

        Set<String> desiredStockCodes = Set.copyOf(
                byCode(diff.desiredTargets()).keySet());
        Set<String> registryStockCodes = Set.copyOf(
                byCode(finalSnapshot.targets()).keySet());
        boolean complete = failure == null
                && finalSnapshot.unmappedStockCodes().isEmpty()
                && afterPhysical.equals(desiredStockCodes)
                && registryStockCodes.equals(afterPhysical);
        RealtimeTargetReconciliationStatus status = complete
                ? RealtimeTargetReconciliationStatus.COMPLETED
                : RealtimeTargetReconciliationStatus.PARTIAL_FAILURE;
        logResult(status, diff, afterPhysical, failure);
        return result(status, diff, beforePhysical, afterPhysical,
                unsubscribeApplied, subscribeApplied,
                finalSnapshot.targets().size(), failure,
                finalSnapshot.unmappedStockCodes());
    }

    private RegistrySnapshot finalRegistrySnapshot(
            List<RealtimeWatchTarget> desiredTargets,
            List<RealtimeWatchTarget> currentTargets,
            Set<String> finalPhysicalStockCodes
    ) {
        Map<String, RealtimeWatchTarget> currentByCode = byCode(currentTargets);
        List<RealtimeWatchTarget> targets = new ArrayList<>();
        Set<String> mapped = new LinkedHashSet<>();
        for (RealtimeWatchTarget desired : desiredTargets) {
            if (finalPhysicalStockCodes.contains(desired.stockCode())) {
                targets.add(desired);
                mapped.add(desired.stockCode());
            }
        }
        finalPhysicalStockCodes.stream()
                .filter(code -> !mapped.contains(code))
                .sorted()
                .map(currentByCode::get)
                .filter(Objects::nonNull)
                .forEach(targets::add);
        Set<String> registryCodes = byCode(targets).keySet();
        List<String> unmapped = finalPhysicalStockCodes.stream()
                .filter(code -> !registryCodes.contains(code))
                .sorted()
                .toList();
        return new RegistrySnapshot(targets, unmapped);
    }

    private boolean sameSnapshot(
            List<RealtimeWatchTarget> current,
            List<RealtimeWatchTarget> intended
    ) {
        return byCode(current).equals(byCode(intended));
    }

    private Map<String, RealtimeWatchTarget> byCode(
            List<RealtimeWatchTarget> targets
    ) {
        Map<String, RealtimeWatchTarget> byCode = new LinkedHashMap<>();
        for (RealtimeWatchTarget target : targets) {
            RealtimeWatchTarget previous = byCode.putIfAbsent(
                    target.stockCode(), target);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate stockCode: " + target.stockCode());
            }
        }
        return byCode;
    }

    private RealtimeTargetReconciliationResult result(
            RealtimeTargetReconciliationStatus status,
            RealtimeTargetDiff diff,
            Set<String> beforePhysical,
            Set<String> afterPhysical,
            List<String> unsubscribeApplied,
            List<String> subscribeApplied,
            int registryCount,
            CommandFailure failure,
            List<String> unmapped
    ) {
        return new RealtimeTargetReconciliationResult(
                status, diff.desiredTargets().size(), beforePhysical.size(),
                afterPhysical.size(), diff.toUnsubscribeStockCodes(),
                unsubscribeApplied,
                diff.toSubscribeTargets().stream()
                        .map(RealtimeWatchTarget::stockCode).toList(),
                subscribeApplied, registryCount,
                failure == null ? null : failure.operation(),
                failure == null ? null : failure.stockCode(),
                failure == null ? null : failure.type(),
                failure == null ? null : failure.message(), unmapped);
    }

    private void logResult(
            RealtimeTargetReconciliationStatus status,
            RealtimeTargetDiff diff,
            Set<String> afterPhysical,
            CommandFailure failure
    ) {
        if (failure == null) {
            log.info("realtime target reconciliation completed - status: {}, "
                            + "beforePhysical: {}, desired: {}, unsubscribeRequested: {}, "
                            + "subscribeRequested: {}, afterPhysical: {}",
                    status, diff.currentPhysicalCount(),
                    diff.desiredTargets().size(),
                    diff.toUnsubscribeStockCodes().size(),
                    diff.toSubscribeTargets().size(), afterPhysical.size());
            return;
        }
        log.warn("realtime target reconciliation partially failed - "
                        + "operation: {}, stockCode: {}, failureType: {}, "
                        + "beforePhysical: {}, desired: {}, afterPhysical: {}",
                failure.operation(), failure.stockCode(), failure.type(),
                diff.currentPhysicalCount(), diff.desiredTargets().size(),
                afterPhysical.size());
    }

    private record RegistrySnapshot(
            List<RealtimeWatchTarget> targets,
            List<String> unmappedStockCodes
    ) {
        private RegistrySnapshot {
            targets = List.copyOf(targets);
            unmappedStockCodes = List.copyOf(unmappedStockCodes);
        }
    }

    private record CommandFailure(
            RealtimeSubscriptionCommandOperation operation,
            String stockCode,
            String type,
            String message
    ) {
        private static CommandFailure of(
                RealtimeSubscriptionCommandOperation operation,
                String stockCode,
                RuntimeException exception
        ) {
            return new CommandFailure(operation, stockCode,
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
        }
    }
}
