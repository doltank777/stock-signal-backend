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
}
