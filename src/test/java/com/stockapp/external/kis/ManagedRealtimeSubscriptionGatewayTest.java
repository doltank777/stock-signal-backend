package com.stockapp.external.kis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedRealtimeSubscriptionGatewayTest {

    @Mock KisWebSocketSessionManager sessionManager;
    @Mock KisWebSocketClient client;
    @Mock KisWebSocketSession session;

    private Set<String> active;
    private ManagedRealtimeSubscriptionGateway gateway;

    @BeforeEach
    void setUp() {
        active = java.util.Collections.synchronizedSet(new HashSet<>());
        gateway = new ManagedRealtimeSubscriptionGateway(
                sessionManager, client);
    }

    @Test
    void snapshotIsImmutableAndMissingOrClosedSessionIsEmpty() {
        when(sessionManager.currentOpenSession())
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(session));
        when(session.activeStockCodes()).thenReturn(List.of("005930"));

        assertThat(gateway.currentActiveStockCodes()).isEmpty();
        Set<String> snapshot = gateway.currentActiveStockCodes();
        assertThat(snapshot).containsExactly("005930");
        assertThatThrownBy(() -> snapshot.add("000660"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void subscribeAppliesOnceAndAlreadyActiveIsIdempotent() {
        prepareOpenSession();
        when(client.subscribe(session, "005930")).thenAnswer(invocation -> {
            active.add("005930");
            return confirmed(KisWebSocketOperation.SUBSCRIBE, "005930");
        });

        var applied = gateway.subscribe("005930");
        var repeated = gateway.subscribe("005930");

        assertThat(applied.status())
                .isEqualTo(RealtimeSubscriptionCommandStatus.APPLIED);
        assertThat(applied.activeAfter()).isTrue();
        assertThat(repeated.status())
                .isEqualTo(RealtimeSubscriptionCommandStatus.ALREADY_ACTIVE);
        verify(client).subscribe(session, "005930");
    }

    @Test
    void unsubscribeAppliesOnceAndAlreadyInactiveIsIdempotent() {
        active.add("005930");
        prepareOpenSession();
        when(client.unsubscribe(session, "005930")).thenAnswer(invocation -> {
            active.remove("005930");
            return confirmed(KisWebSocketOperation.UNSUBSCRIBE, "005930");
        });

        var applied = gateway.unsubscribe("005930");
        var repeated = gateway.unsubscribe("005930");

        assertThat(applied.status())
                .isEqualTo(RealtimeSubscriptionCommandStatus.APPLIED);
        assertThat(applied.activeAfter()).isFalse();
        assertThat(repeated.status()).isEqualTo(
                RealtimeSubscriptionCommandStatus.ALREADY_INACTIVE);
        verify(client).unsubscribe(session, "005930");
    }

    @Test
    void capacityBlocksOnlyNewSubscriptionsAndNeverUnsubscribe() {
        for (int index = 0; index < 44; index++) {
            active.add(code(index));
        }
        prepareOpenSession();

        assertThat(gateway.subscribe(code(0)).status())
                .isEqualTo(RealtimeSubscriptionCommandStatus.ALREADY_ACTIVE);
        assertThatThrownBy(() -> gateway.subscribe("999999"))
                .isInstanceOf(
                        RealtimeSubscriptionCapacityExceededException.class)
                .hasMessageContaining("currentActiveCount: 44");
        when(client.unsubscribe(session, code(43))).thenAnswer(invocation -> {
            active.remove(code(43));
            return confirmed(KisWebSocketOperation.UNSUBSCRIBE, code(43));
        });
        assertThat(gateway.unsubscribe(code(43)).status())
                .isEqualTo(RealtimeSubscriptionCommandStatus.APPLIED);
        verify(client, never()).subscribe(session, "999999");
    }

    @Test
    void missingSessionRejectsMutationsAndInvalidCodes() {
        when(sessionManager.currentOpenSession())
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> gateway.subscribe("005930"))
                .isInstanceOf(
                        RealtimeSubscriptionSessionUnavailableException.class);
        assertThatThrownBy(() -> gateway.unsubscribe("005930"))
                .isInstanceOf(
                        RealtimeSubscriptionSessionUnavailableException.class);
        assertThatThrownBy(() -> gateway.subscribe(" "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).subscribe(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void postAckTrackerMismatchFailsClosed() {
        prepareOpenSession();
        when(client.subscribe(session, "005930")).thenReturn(
                confirmed(KisWebSocketOperation.SUBSCRIBE, "005930"));

        assertThatThrownBy(() -> gateway.subscribe("005930"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing from physical tracker");
    }

    @Test
    void commandMonitorSerializesThroughAckAndPreventsCapacityRace()
            throws Exception {
        prepareOpenSession();
        for (int index = 0; index < 39; index++) {
            active.add(code(index));
        }
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger concurrentCalls = new AtomicInteger();
        AtomicInteger maxConcurrentCalls = new AtomicInteger();
        when(client.subscribe(
                org.mockito.ArgumentMatchers.eq(session),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    int concurrent = concurrentCalls.incrementAndGet();
                    maxConcurrentCalls.accumulateAndGet(
                            concurrent, Math::max);
                    String stockCode = invocation.getArgument(1);
                    if (stockCode.equals("900001")) {
                        firstEntered.countDown();
                        assertThat(releaseFirst.await(2, TimeUnit.SECONDS))
                                .isTrue();
                    }
                    active.add(stockCode);
                    concurrentCalls.decrementAndGet();
                    return confirmed(
                            KisWebSocketOperation.SUBSCRIBE, stockCode);
                });

        List<Throwable> failures = java.util.Collections.synchronizedList(
                new ArrayList<>());
        Thread first = new Thread(() -> invokeSubscribe("900001", failures));
        Thread second = new Thread(() -> invokeSubscribe("900002", failures));
        first.start();
        assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
        second.start();
        releaseFirst.countDown();
        first.join(2_000);
        second.join(2_000);

        assertThat(maxConcurrentCalls).hasValue(1);
        assertThat(active).hasSize(40).contains("900001");
        assertThat(failures).singleElement()
                .isInstanceOf(
                        RealtimeSubscriptionCapacityExceededException.class);
    }

    private void prepareOpenSession() {
        when(sessionManager.currentOpenSession()).thenReturn(
                java.util.Optional.of(session));
        when(session.isOpen()).thenReturn(true);
        when(session.commandMonitor()).thenReturn(new Object());
        when(session.activeStockCodes()).thenAnswer(
                invocation -> List.copyOf(active));
    }

    private void invokeSubscribe(String stockCode, List<Throwable> failures) {
        try {
            gateway.subscribe(stockCode);
        } catch (Throwable failure) {
            failures.add(failure);
        }
    }

    private KisWebSocketSubscriptionResult confirmed(
            KisWebSocketOperation operation,
            String stockCode
    ) {
        return new KisWebSocketSubscriptionResult(
                "session-1", "H0STCNT0", stockCode, operation,
                KisSubscriptionStatus.CONFIRMED, null, "SUCCESS");
    }

    private String code(int index) {
        return "%06d".formatted(index);
    }
}
