package com.stockapp.external.kis.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockapp.external.kis.KisSubscriptionStatus;
import com.stockapp.external.kis.KisWebSocketControlResponseParser;
import com.stockapp.external.kis.KisWebSocketOperation;
import com.stockapp.external.kis.KisWebSocketSubscriptionTracker;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisWebSocketProbeHandlerTest {

    @Test
    void handlesControlAckAndEchoesPingPong() throws Exception {
        KisWebSocketSubscriptionTracker tracker =
                new KisWebSocketSubscriptionTracker();
        KisWebSocketProbeHandler handler = handler(tracker);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);

        handler.handleTextMessage(session, new TextMessage("""
                {"header":{"tr_id":"H0STCNT0","tr_key":"005930"},
                 "body":{"rt_cd":"0","msg1":"SUBSCRIBE SUCCESS"}}
                """));
        TextMessage pingPong = new TextMessage("{\"header\":{\"tr_id\":\"PINGPONG\"}}");
        handler.handleTextMessage(session, pingPong);

        assertThat(tracker.snapshot("session-1").getFirst().status())
                .isEqualTo(KisSubscriptionStatus.CONFIRMED);
        verify(session).sendMessage(pingPong);
    }

    @Test
    void ignoresTradePayloadWithoutBusinessDependencies() throws Exception {
        KisWebSocketSubscriptionTracker tracker =
                new KisWebSocketSubscriptionTracker();
        KisWebSocketProbeHandler handler = handler(tracker);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");

        handler.handleTextMessage(
                session, new TextMessage("0|H0STCNT0|1|005930^trade"));

        assertThat(tracker.snapshot("session-1")).isEmpty();
    }

    @Test
    void completesPendingRequestOnCloseAndTransportError() {
        KisWebSocketSubscriptionTracker tracker =
                new KisWebSocketSubscriptionTracker();
        KisWebSocketProbeHandler handler = handler(tracker);
        WebSocketSession first = mock(WebSocketSession.class);
        when(first.getId()).thenReturn("session-1");
        tracker.registerPending("session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        assertThat(tracker.snapshot("session-1").getFirst().messageCode())
                .isEqualTo("CONNECTION_CLOSED");

        WebSocketSession second = mock(WebSocketSession.class);
        when(second.getId()).thenReturn("session-2");
        tracker.registerPending("session-2", "H0STCNT0", "000660",
                KisWebSocketOperation.SUBSCRIBE);
        handler.handleTransportError(second, new IllegalStateException("broken"));
        assertThat(tracker.snapshot("session-2").getFirst().messageCode())
                .isEqualTo("TRANSPORT_ERROR");
    }

    private KisWebSocketProbeHandler handler(
            KisWebSocketSubscriptionTracker tracker) {
        return new KisWebSocketProbeHandler(
                new KisWebSocketControlResponseParser(new ObjectMapper()), tracker);
    }
}
