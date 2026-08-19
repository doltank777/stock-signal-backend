package com.stockapp.external.kis;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KisWebSocketSubscriptionTrackerTest {

    @Test
    void correlatesBySessionTrAndStockAndTracksImmutableResults() throws Exception {
        KisWebSocketSubscriptionTracker tracker = new KisWebSocketSubscriptionTracker();
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.registerPending("session-2", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);

        assertThat(tracker.snapshot("session-1").getFirst().status())
                .isEqualTo(KisSubscriptionStatus.PENDING);
        assertThat(tracker.handle("session-1", response("005930", "0"))).isTrue();
        assertThat(tracker.handle("session-1", response("005930", "0"))).isFalse();

        KisWebSocketSubscriptionResult result = tracker.awaitResult(
                "session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE, Duration.ofMillis(10));
        assertThat(result.status()).isEqualTo(KisSubscriptionStatus.CONFIRMED);
        assertThat(tracker.snapshot("session-2").getFirst().status())
                .isEqualTo(KisSubscriptionStatus.PENDING);
        assertThat(tracker.snapshot("session-1"))
                .isUnmodifiable();
    }

    @Test
    void tracksRejectionTimeoutCloseAndUnknownKey() throws Exception {
        KisWebSocketSubscriptionTracker tracker = new KisWebSocketSubscriptionTracker();
        tracker.registerPending("s1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        assertThat(tracker.handle("s1", response("000660", "9"))).isFalse();
        assertThat(tracker.handle("s1", response("005930", "9"))).isTrue();
        assertThat(tracker.snapshot("s1").getFirst().status())
                .isEqualTo(KisSubscriptionStatus.FAILED);

        tracker.registerPending("s2", "H0STCNT0", "000660",
                KisWebSocketOperation.SUBSCRIBE);
        assertThat(tracker.awaitResult("s2", "H0STCNT0", "000660",
                KisWebSocketOperation.SUBSCRIBE, Duration.ofMillis(1)).message())
                .isEqualTo("ACK_TIMEOUT");

        tracker.registerPending("s3", "H0STCNT0", "035420",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.failPendingForSession("s3", "CONNECTION_CLOSED", "closed");
        assertThat(tracker.snapshot("s3").getFirst().messageCode())
                .isEqualTo("CONNECTION_CLOSED");
    }

    @Test
    void separatesHistoryFromActiveStateAcrossSubscribeUnsubscribeResubscribe()
            throws Exception {
        KisWebSocketSubscriptionTracker tracker =
                new KisWebSocketSubscriptionTracker();

        complete(tracker, KisWebSocketOperation.SUBSCRIBE);
        assertThat(tracker.activeStockCodes("session-1"))
                .containsExactly("005930");

        complete(tracker, KisWebSocketOperation.UNSUBSCRIBE);
        assertThat(tracker.activeStockCodes("session-1")).isEmpty();

        complete(tracker, KisWebSocketOperation.SUBSCRIBE);
        assertThat(tracker.activeStockCodes("session-1"))
                .containsExactly("005930");
        assertThat(tracker.snapshot("session-1"))
                .extracting(KisWebSocketSubscriptionResult::operation)
                .containsExactly(
                        KisWebSocketOperation.SUBSCRIBE,
                        KisWebSocketOperation.UNSUBSCRIBE,
                        KisWebSocketOperation.SUBSCRIBE);
    }

    @Test
    void failedUnsubscribeDoesNotRemoveActiveStock() {
        KisWebSocketSubscriptionTracker tracker =
                new KisWebSocketSubscriptionTracker();
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.handle("session-1", response("005930", "0"));
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.UNSUBSCRIBE);
        tracker.handle("session-1", response("005930", "9"));

        assertThat(tracker.activeStockCodes("session-1"))
                .containsExactly("005930");
    }

    @Test
    void correlatesAckLikeResponseWithoutKeysOnlyToUniquePendingRequest()
            throws Exception {
        KisWebSocketSubscriptionTracker tracker = new KisWebSocketSubscriptionTracker();
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.handle("session-1", response("005930", "0"));
        KisWebSocketSubscriptionRequest unsubscribe = tracker.registerPending(
                "session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.UNSUBSCRIBE);

        assertThat(tracker.handle("session-1", ack(null, ""))).isTrue();
        assertThat(tracker.awaitResult(unsubscribe, Duration.ofMillis(10)).status())
                .isEqualTo(KisSubscriptionStatus.CONFIRMED);
        assertThat(tracker.activeStockCodes("session-1")).isEmpty();
        assertThat(tracker.handle("session-1", ack(null, ""))).isFalse();
    }

    @Test
    void rejectsAmbiguousOrExplicitlyMismatchedFallback() {
        KisWebSocketSubscriptionTracker tracker = new KisWebSocketSubscriptionTracker();
        assertThat(tracker.handle("session-1", ack(null, ""))).isFalse();

        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.registerPending("session-1", "H0STCNT0", "000660",
                KisWebSocketOperation.SUBSCRIBE);
        assertThat(tracker.handle("session-1", ack(null, ""))).isFalse();
        assertThat(tracker.handle("session-1", ack("WRONG_TR", ""))).isFalse();
        assertThat(tracker.activeStockCodes("session-1")).isEmpty();
    }

    @Test
    void exactCorrelationTakesPriorityWhenMultipleRequestsArePending() {
        KisWebSocketSubscriptionTracker tracker = new KisWebSocketSubscriptionTracker();
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.registerPending("session-1", "H0STCNT0", "000660",
                KisWebSocketOperation.SUBSCRIBE);

        assertThat(tracker.handle("session-1", response("005930", "0"))).isTrue();
        assertThat(tracker.snapshot("session-1"))
                .extracting(KisWebSocketSubscriptionResult::status)
                .containsExactly(KisSubscriptionStatus.CONFIRMED,
                        KisSubscriptionStatus.PENDING);
    }

    @Test
    void isolatesSameTrIdAndStockCodeBySession() {
        KisWebSocketSubscriptionTracker tracker = new KisWebSocketSubscriptionTracker();
        tracker.registerPending("session-A", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.registerPending("session-B", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);

        assertThat(tracker.handle("session-A", response("005930", "0"))).isTrue();

        assertThat(tracker.snapshot("session-A").getFirst().status())
                .isEqualTo(KisSubscriptionStatus.CONFIRMED);
        assertThat(tracker.snapshot("session-B").getFirst().status())
                .isEqualTo(KisSubscriptionStatus.PENDING);
    }

    private void complete(
            KisWebSocketSubscriptionTracker tracker,
            KisWebSocketOperation operation
    ) throws Exception {
        KisWebSocketSubscriptionRequest request = tracker.registerPending(
                "session-1", "H0STCNT0", "005930", operation);
        assertThat(tracker.handle(
                "session-1", response("005930", "0"))).isTrue();
        assertThat(tracker.awaitResult(request, Duration.ofMillis(10)).status())
                .isEqualTo(KisSubscriptionStatus.CONFIRMED);
    }

    private KisWebSocketControlResponse response(String stockCode, String returnCode) {
        return new KisWebSocketControlResponse(
                "H0STCNT0", stockCode, returnCode,
                "0".equals(returnCode) ? null : "OPSP8996",
                "0".equals(returnCode) ? "SUBSCRIBE SUCCESS" : "rejected");
    }

    private KisWebSocketControlResponse ack(String trId, String trKey) {
        return new KisWebSocketControlResponse(
                trId, trKey, "0", "OPSP0000", "accepted");
    }
}
