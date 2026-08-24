package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.stock.MarketType;
import com.stockapp.external.kis.KisWebSocketException;
import com.stockapp.external.kis.ManagedRealtimeSubscriptionGateway;
import com.stockapp.external.kis.RealtimeSubscriptionCommandOperation;
import com.stockapp.external.kis.RealtimeSubscriptionSessionUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealtimeTargetReconciliationServiceTest {

    @Test
    void noOpSkipsCommandsAndRegistryReplace() {
        var registry = spy(new RealtimeWatchTargetRegistry());
        registry.replace(List.of(target(1L, "A", 1L)));
        var gateway = gatewayWithState("A");
        var service = service(registry, gateway);

        var result = service.reconcile(selection(desired(1L, "A", 1L)));

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.NO_OP);
        verify(gateway, never()).subscribe(anyString());
        verify(gateway, never()).unsubscribe(anyString());
        verify(registry, times(1)).replace(List.of(target(1L, "A", 1L)));
    }

    @Test
    void metadataOnlyAtomicallyReplacesRegistryWithoutCommands() {
        var registry = registry(target(1L, "A", 1L));
        var gateway = gatewayWithState("A");

        var result = service(registry, gateway).reconcile(
                selection(desired(1L, "A", 3L, 1L)));

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
        assertThat(registry.findByStockCode("A").orElseThrow().conditionIds())
                .containsExactly(1L, 3L);
        verify(gateway, never()).subscribe(anyString());
        verify(gateway, never()).unsubscribe(anyString());
        verify(gateway, times(1)).currentActiveStockCodes();
    }

    @Test
    void addRemoveAndReplacementConvergeRegistryToPhysicalDesiredState() {
        var registry = registry(
                target(1L, "A", 1L), target(2L, "B", 2L));
        var events = new ArrayList<String>();
        var gateway = gatewayWithState(events, "A", "B");

        var result = service(registry, gateway).reconcile(selection(
                desired(1L, "A", 1L), desired(3L, "C", 3L)));

        assertThat(events).containsExactly("unsubscribe:B", "subscribe:C");
        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
        assertThat(result.unsubscribeAppliedStockCodes()).containsExactly("B");
        assertThat(result.subscribeAppliedStockCodes()).containsExactly("C");
        assertThat(registry.findAll().keySet()).containsExactly("A", "C");
    }

    @Test
    void addOnlySubscribesMissingTarget() {
        var registry = registry(target(1L, "A", 1L));
        var gateway = gatewayWithState("A");

        var result = service(registry, gateway).reconcile(selection(
                desired(1L, "A", 1L), desired(2L, "B", 2L)));

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
        assertThat(result.subscribeAppliedStockCodes()).containsExactly("B");
        verify(gateway, never()).unsubscribe(anyString());
    }

    @Test
    void removeOnlyUnsubscribesObsoleteTarget() {
        var registry = registry(
                target(1L, "A", 1L), target(2L, "B", 2L));
        var gateway = gatewayWithState("A", "B");

        var result = service(registry, gateway).reconcile(
                selection(desired(1L, "A", 1L)));

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
        assertThat(result.unsubscribeAppliedStockCodes()).containsExactly("B");
        verify(gateway, never()).subscribe(anyString());
    }

    @Test
    void desiredEmptyRemovesAllPhysicalTargetsAndRegistryMetadata() {
        var registry = registry(
                target(1L, "A", 1L), target(2L, "B", 2L));
        var gateway = gatewayWithState("A", "B");

        var result = service(registry, gateway).reconcile(selection());

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
        assertThat(result.unsubscribeAppliedStockCodes())
                .containsExactly("A", "B");
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void emptyPhysicalStateCleansStaleRegistryWithoutCommands() {
        var registry = registry(
                target(1L, "A", 1L), target(2L, "B", 2L));
        var gateway = gatewayWithState();

        var result = service(registry, gateway).reconcile(selection());

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
        assertThat(registry.isEmpty()).isTrue();
        verify(gateway, never()).subscribe(anyString());
        verify(gateway, never()).unsubscribe(anyString());
    }

    @Test
    void fortyForFortyRunsEveryUnsubscribeBeforeAnySubscribe() {
        List<RealtimeWatchTarget> current = new ArrayList<>();
        List<DesiredRealtimeTarget> desired = new ArrayList<>();
        List<String> active = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            current.add(target((long) index, code(index), (long) index));
            active.add(code(index));
            desired.add(index < 30
                    ? desired((long) index, code(index), (long) index)
                    : desired((long) index + 40, code(index + 40),
                    (long) index + 40));
        }
        var events = new ArrayList<String>();
        var gateway = gatewayWithState(events, active.toArray(String[]::new));

        var result = service(registry(current), gateway)
                .reconcile(selection(desired));

        assertThat(events.subList(0, 10)).allMatch(
                event -> event.startsWith("unsubscribe:"));
        assertThat(events.subList(10, 20)).allMatch(
                event -> event.startsWith("subscribe:"));
        assertThat(result.afterPhysicalCount()).isEqualTo(40);
        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
    }

    @Test
    void unsubscribeFailureStopsAllLaterCommandsAndPreservesPhysicalMetadata() {
        var registry = registry(target(1L, "A", 1L),
                target(2L, "B", 2L), target(3L, "C", 3L),
                target(4L, "D", 4L));
        var gateway = gatewayWithState("A", "B", "C", "D");
        doThrow(failure()).when(gateway).unsubscribe("B");

        var result = service(registry, gateway).reconcile(selection(
                desired(3L, "C", 30L), desired(4L, "D", 40L),
                desired(5L, "E", 5L), desired(6L, "F", 6L)));

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.PARTIAL_FAILURE);
        assertThat(result.failedOperation()).isEqualTo(
                RealtimeSubscriptionCommandOperation.UNSUBSCRIBE);
        assertThat(result.failedStockCode()).isEqualTo("B");
        assertThat(result.unsubscribeAppliedStockCodes()).containsExactly("A");
        assertThat(registry.findAll().keySet()).containsExactly("C", "D", "B");
        assertThat(registry.findByStockCode("B").orElseThrow().conditionIds())
                .containsExactly(2L);
        assertThat(registry.findByStockCode("C").orElseThrow().conditionIds())
                .containsExactly(30L);
        verify(gateway, never()).subscribe(anyString());
    }

    @Test
    void subscribeFailureStopsLowerRanksWithoutRollback() {
        var registry = registry(target(1L, "A", 1L),
                target(2L, "B", 2L));
        var gateway = gatewayWithState("A", "B");
        doThrow(failure()).when(gateway).subscribe("D");

        var result = service(registry, gateway).reconcile(selection(
                desired(1L, "A", 10L), desired(3L, "C", 3L),
                desired(4L, "D", 4L), desired(5L, "E", 5L)));

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.PARTIAL_FAILURE);
        assertThat(result.subscribeAppliedStockCodes()).containsExactly("C");
        assertThat(registry.findAll().keySet()).containsExactly("A", "C");
        verify(gateway, never()).subscribe("E");
        verify(gateway, never()).subscribe("B");
    }

    @Test
    void unmappedPhysicalIsReportedWhenUnsubscribeFails() {
        var registry = registry();
        var gateway = gatewayWithState("C");
        doThrow(failure()).when(gateway).unsubscribe("C");

        var result = service(registry, gateway).reconcile(selection());

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.PARTIAL_FAILURE);
        assertThat(result.unmappedPhysicalStockCodes()).containsExactly("C");
        assertThat(result.registryCount()).isZero();
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void unavailableSessionWithDesiredTargetsReturnsPartialAndClearsStaleRegistry() {
        var registry = registry(target(9L, "OLD", 9L));
        var gateway = gatewayWithState();
        doThrow(new RealtimeSubscriptionSessionUnavailableException())
                .when(gateway).subscribe("A");

        var result = service(registry, gateway).reconcile(
                selection(desired(1L, "A", 1L)));

        assertThat(result.status()).isEqualTo(
                RealtimeTargetReconciliationStatus.PARTIAL_FAILURE);
        assertThat(result.afterPhysicalCount()).isZero();
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void repeatedReconciliationIsIdempotent() {
        var registry = registry();
        var gateway = gatewayWithState();
        var service = service(registry, gateway);
        var selection = selection(desired(1L, "A", 1L));

        assertThat(service.reconcile(selection).status()).isEqualTo(
                RealtimeTargetReconciliationStatus.COMPLETED);
        assertThat(service.reconcile(selection).status()).isEqualTo(
                RealtimeTargetReconciliationStatus.NO_OP);
        verify(gateway, times(1)).subscribe("A");
    }

    @Test
    void registryCommitFailurePropagatesWithoutPhysicalRollback() {
        var registry = mock(RealtimeWatchTargetRegistry.class);
        when(registry.findAll()).thenReturn(java.util.Map.of());
        doThrow(new IllegalStateException("registry failure"))
                .when(registry).replace(org.mockito.ArgumentMatchers.anyList());
        var gateway = gatewayWithState();

        assertThatThrownBy(() -> service(registry, gateway).reconcile(
                selection(desired(1L, "A", 1L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("registry failure");
        verify(gateway, times(1)).subscribe("A");
        verify(gateway, never()).unsubscribe("A");
    }

    @Test
    void concurrentCallsAreSerializedAcrossSnapshotApplyAndCommit()
            throws Exception {
        var registry = registry();
        var gateway = gatewayWithState();
        CountDownLatch firstSnapshotEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstSnapshot = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(gateway.currentActiveStockCodes()).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstSnapshotEntered.countDown();
                assertThat(releaseFirstSnapshot.await(5, TimeUnit.SECONDS))
                        .isTrue();
            }
            return Set.of();
        });
        var service = service(registry, gateway);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.reconcile(selection()));
            assertThat(firstSnapshotEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.reconcile(selection()));
            assertThat(calls.get()).isEqualTo(1);
            releaseFirstSnapshot.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(
                    RealtimeTargetReconciliationStatus.NO_OP);
            assertThat(second.get(5, TimeUnit.SECONDS).status()).isEqualTo(
                    RealtimeTargetReconciliationStatus.NO_OP);
        }
        assertThat(calls.get()).isEqualTo(2);
    }

    private RealtimeTargetReconciliationService service(
            RealtimeWatchTargetRegistry registry,
            ManagedRealtimeSubscriptionGateway gateway
    ) {
        return new RealtimeTargetReconciliationService(registry,
                new RealtimeTargetDiffPlanner(new RealtimeWatchTargetMapper()),
                gateway);
    }

    private RealtimeWatchTargetRegistry registry(
            RealtimeWatchTarget... targets
    ) {
        return registry(List.of(targets));
    }

    private RealtimeWatchTargetRegistry registry(
            List<RealtimeWatchTarget> targets
    ) {
        var registry = new RealtimeWatchTargetRegistry();
        registry.replace(targets);
        return registry;
    }

    private ManagedRealtimeSubscriptionGateway gatewayWithState(
            String... stockCodes
    ) {
        return gatewayWithState(new ArrayList<>(), stockCodes);
    }

    private ManagedRealtimeSubscriptionGateway gatewayWithState(
            List<String> events,
            String... stockCodes
    ) {
        var gateway = mock(ManagedRealtimeSubscriptionGateway.class);
        Set<String> active = new LinkedHashSet<>(List.of(stockCodes));
        when(gateway.currentActiveStockCodes()).thenAnswer(
                invocation -> Set.copyOf(active));
        doAnswer(invocation -> {
            String code = invocation.getArgument(0);
            events.add("unsubscribe:" + code);
            active.remove(code);
            return null;
        }).when(gateway).unsubscribe(anyString());
        doAnswer(invocation -> {
            String code = invocation.getArgument(0);
            events.add("subscribe:" + code);
            active.add(code);
            return null;
        }).when(gateway).subscribe(anyString());
        return gateway;
    }

    private KisWebSocketException failure() {
        return new KisWebSocketException("command failed", (Throwable) null);
    }

    private OperationalRealtimeTargetSelection selection(
            DesiredRealtimeTarget... targets
    ) {
        return selection(List.of(targets));
    }

    private OperationalRealtimeTargetSelection selection(
            List<DesiredRealtimeTarget> targets
    ) {
        return new OperationalRealtimeTargetSelection(
                RealtimeWatchPolicy.CAPACITY, targets.size(), targets,
                List.of());
    }

    private DesiredRealtimeTarget desired(
            Long stockId, String stockCode, Long... conditionIds
    ) {
        List<DesiredRealtimeCondition> conditions =
                java.util.Arrays.stream(conditionIds)
                        .map(id -> new DesiredRealtimeCondition(
                                id, "condition-" + id, 100, 50))
                        .toList();
        DesiredRealtimeCondition first = conditions.getFirst();
        return new DesiredRealtimeTarget(stockId, stockCode,
                "stock-" + stockCode, MarketType.KOSPI,
                first.priority(), first.screeningScore(), conditions);
    }

    private RealtimeWatchTarget target(
            Long stockId, String stockCode, Long... conditionIds
    ) {
        return new RealtimeWatchTarget(
                stockId, stockCode, List.of(conditionIds));
    }

    private String code(int index) {
        return "%06d".formatted(index);
    }
}
