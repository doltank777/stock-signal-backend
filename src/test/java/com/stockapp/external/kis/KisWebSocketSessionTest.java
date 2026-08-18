package com.stockapp.external.kis;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisWebSocketSessionTest {

    @Test
    void exposesSessionStateAndImmutableRequestedStockCodes() {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);
        when(webSocketSession.getId()).thenReturn("session-1");
        when(webSocketSession.isOpen()).thenReturn(true);
        List<String> source = new ArrayList<>(
                List.of("005930", "000660", "005930"));

        KisWebSocketSession session = new KisWebSocketSession(
                webSocketSession, source);
        source.clear();

        assertThat(session.sessionId()).isEqualTo("session-1");
        assertThat(session.isOpen()).isTrue();
        assertThat(session.requestedStockCodes())
                .containsExactly("005930", "000660");
        assertThatThrownBy(() -> session.requestedStockCodes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void closesOpenSessionAndSkipsAlreadyClosedSession() throws IOException {
        WebSocketSession openSession = mock(WebSocketSession.class);
        when(openSession.isOpen()).thenReturn(true);
        KisWebSocketSession openHandle = new KisWebSocketSession(
                openSession, List.of("005930"));

        openHandle.close();

        verify(openSession).close();

        WebSocketSession closedSession = mock(WebSocketSession.class);
        when(closedSession.isOpen()).thenReturn(false);
        KisWebSocketSession closedHandle = new KisWebSocketSession(
                closedSession, List.of("000660"));

        closedHandle.close();

        verify(closedSession, never()).close();
    }

    @Test
    void propagatesCloseFailure() throws IOException {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);
        when(webSocketSession.isOpen()).thenReturn(true);
        IOException failure = new IOException("close failed");
        org.mockito.Mockito.doThrow(failure).when(webSocketSession).close();
        KisWebSocketSession session = new KisWebSocketSession(
                webSocketSession, List.of("005930"));

        assertThatIOException()
                .isThrownBy(session::close)
                .isSameAs(failure);
    }

    @Test
    void rejectsInvalidRequestedStockCodes() {
        WebSocketSession webSocketSession = mock(WebSocketSession.class);

        assertThatNullPointerException()
                .isThrownBy(() -> new KisWebSocketSession(
                        null, List.of("005930")))
                .withMessage("session is required");
        assertThatNullPointerException()
                .isThrownBy(() -> new KisWebSocketSession(
                        webSocketSession, null))
                .withMessage("requestedStockCodes are required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisWebSocketSession(
                        webSocketSession, List.of()))
                .withMessage("at least one requestedStockCode is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisWebSocketSession(
                        webSocketSession, List.of(" ")))
                .withMessage("stockCode must not be blank");
        assertThatNullPointerException()
                .isThrownBy(() -> new KisWebSocketSession(
                        webSocketSession, Arrays.asList("005930", null)))
                .withMessage("stockCode is required");
    }
}
