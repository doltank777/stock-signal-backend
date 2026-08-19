package com.stockapp.external.kis.probe;

import com.stockapp.external.kis.KisSubscriptionStatus;
import com.stockapp.external.kis.KisWebSocketClient;
import com.stockapp.external.kis.KisWebSocketException;
import com.stockapp.external.kis.KisWebSocketOperation;
import com.stockapp.external.kis.KisWebSocketSession;
import com.stockapp.external.kis.KisWebSocketSubscriptionResult;
import com.stockapp.external.kis.KisWebSocketSubscriptionTracker;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisWebSocketProbeRunnerTest {

    @Test
    void subscribesOneNormalizedListSummarizesAndCloses() throws Exception {
        KisWebSocketProbeProperties properties = properties("005930,000660,005930");
        KisWebSocketClient client = mock(KisWebSocketClient.class);
        KisWebSocketSession session = mock(KisWebSocketSession.class);
        List<KisWebSocketSubscriptionResult> results = List.of(
                result("005930", KisSubscriptionStatus.CONFIRMED, null),
                result("000660", KisSubscriptionStatus.CONFIRMED, null));
        when(client.connectAndSubscribe(List.of("005930", "000660")))
                .thenReturn(session);
        when(session.subscriptionResults()).thenReturn(results);
        KisWebSocketProbeRunner runner = new KisWebSocketProbeRunner(
                properties, client, new KisWebSocketSubscriptionTracker());

        KisWebSocketProbeSummary summary = runner.execute();

        assertThat(summary.requestedStockCodes())
                .containsExactly("005930", "000660");
        assertThat(summary.confirmedCount()).isEqualTo(2);
        assertThat(summary.failedCount()).isZero();
        verify(session).close();
    }

    @Test
    void invalidInputFailsBeforeClientCall() {
        KisWebSocketProbeProperties properties = properties("");
        KisWebSocketClient client = mock(KisWebSocketClient.class);
        KisWebSocketProbeRunner runner = new KisWebSocketProbeRunner(
                properties, client, new KisWebSocketSubscriptionTracker());

        assertThatThrownBy(runner::execute)
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
    }

    @Test
    void rejectionIsReportedAndPropagated() {
        KisWebSocketProbeProperties properties = properties("005930,000660");
        KisWebSocketClient client = mock(KisWebSocketClient.class);
        KisWebSocketSubscriptionTracker tracker =
                new KisWebSocketSubscriptionTracker();
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        tracker.handle("session-1", new com.stockapp.external.kis.KisWebSocketControlResponse(
                "H0STCNT0", "005930", "0", null, "SUBSCRIBE SUCCESS"));
        tracker.registerPending("session-1", "H0STCNT0", "000660",
                KisWebSocketOperation.SUBSCRIBE);
        KisWebSocketSubscriptionResult failure = result(
                "000660", KisSubscriptionStatus.FAILED, "OPSP0008");
        tracker.handle("session-1", new com.stockapp.external.kis.KisWebSocketControlResponse(
                "H0STCNT0", "000660", "9", "OPSP0008", "MAX SUBSCRIBE OVER"));
        KisWebSocketException exception = new KisWebSocketException(
                "rejected", failure);
        when(client.connectAndSubscribe(List.of("005930", "000660")))
                .thenThrow(exception);
        KisWebSocketProbeRunner runner = new KisWebSocketProbeRunner(
                properties, client, tracker);

        assertThatThrownBy(runner::execute).isSameAs(exception);
    }

    @Test
    void closeFailureIsNotHidden() throws Exception {
        KisWebSocketProbeProperties properties = properties("005930");
        KisWebSocketClient client = mock(KisWebSocketClient.class);
        KisWebSocketSession session = mock(KisWebSocketSession.class);
        when(client.connectAndSubscribe(List.of("005930"))).thenReturn(session);
        when(session.subscriptionResults()).thenReturn(List.of(
                result("005930", KisSubscriptionStatus.CONFIRMED, null)));
        IOException closeFailure = new IOException("close failed");
        doThrow(closeFailure).when(session).close();
        KisWebSocketProbeRunner runner = new KisWebSocketProbeRunner(
                properties, client, new KisWebSocketSubscriptionTracker());

        assertThatThrownBy(runner::execute).isSameAs(closeFailure);
        verify(session).close();
    }

    private KisWebSocketProbeProperties properties(String stockCodes) {
        KisWebSocketProbeProperties properties = new KisWebSocketProbeProperties();
        properties.setStockCodes(stockCodes);
        return properties;
    }

    private KisWebSocketSubscriptionResult result(
            String stockCode,
            KisSubscriptionStatus status,
            String messageCode
    ) {
        return new KisWebSocketSubscriptionResult(
                "session-1", "H0STCNT0", stockCode,
                KisWebSocketOperation.SUBSCRIBE, status, messageCode,
                messageCode == null ? "SUBSCRIBE SUCCESS" : "MAX SUBSCRIBE OVER");
    }
}
