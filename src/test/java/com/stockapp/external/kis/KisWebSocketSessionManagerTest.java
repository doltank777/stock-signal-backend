package com.stockapp.external.kis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KisWebSocketSessionManagerTest {

    @Mock private KisWebSocketClient client;
    private KisWebSocketSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new KisWebSocketSessionManager(client);
    }

    @Test
    void acceptsEmptyTargetsWithoutConnecting() {
        manager.connectAll(List.of());
        assertThat(manager.isEmpty()).isTrue();
        assertThat(manager.sessionCount()).isZero();
        verifyNoInteractions(client);
    }

    @Test
    void connectsAllUniqueTargetsThroughOneSessionWithoutCapacityGuard() {
        KisWebSocketSession session = mock(KisWebSocketSession.class);
        when(client.connectAndSubscribe(anyList())).thenReturn(session);
        List<String> stockCodes = java.util.stream.IntStream.range(0, 44)
                .mapToObj(index -> "%06d".formatted(index)).toList();

        manager.connectAll(stockCodes);

        verify(client).connectAndSubscribe(org.mockito.ArgumentMatchers.<List<String>>argThat(
                requested -> requested.equals(stockCodes)));
        assertThat(manager.sessions()).containsExactly(session);
        assertThat(manager.sessionCount()).isOne();
    }

    @Test
    void removesDuplicatesAndPreservesOrder() {
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(mock(KisWebSocketSession.class));
        manager.connectAll(List.of("005930", "000660", "005930"));
        verify(client).connectAndSubscribe(List.of("005930", "000660"));
    }

    @Test
    void publishesOnlyAfterClientReturnsAndRejectsDuplicateConnect() {
        KisWebSocketSession session = mock(KisWebSocketSession.class);
        when(client.connectAndSubscribe(anyList())).thenAnswer(invocation -> {
            assertThat(manager.isEmpty()).isTrue();
            return session;
        });
        manager.connectAll(List.of("005930"));

        assertThatIllegalStateException()
                .isThrownBy(() -> manager.connectAll(List.of("000660")))
                .withMessage("KIS WebSocket sessions are already active");
    }

    @Test
    void connectionFailureDoesNotPublishSession() {
        KisWebSocketException failure = new KisWebSocketException(
                "connect failed", new IOException("network failed"));
        when(client.connectAndSubscribe(anyList())).thenThrow(failure);
        assertThatThrownBy(() -> manager.connectAll(List.of("005930")))
                .isSameAs(failure);
        assertThat(manager.isEmpty()).isTrue();
    }

    @Test
    void closeClearsStateAndPropagatesFailure() throws Exception {
        KisWebSocketSession session = mock(KisWebSocketSession.class);
        IOException failure = new IOException("close failed");
        doThrow(failure).when(session).close();
        when(client.connectAndSubscribe(anyList())).thenReturn(session);
        manager.connectAll(List.of("005930"));

        assertThatThrownBy(manager::closeAll).isSameAs(failure);
        assertThat(manager.isEmpty()).isTrue();
    }

    @Test
    void shutdownClosesWithoutPropagatingFailure() throws Exception {
        KisWebSocketSession session = mock(KisWebSocketSession.class);
        doThrow(new IOException("close failed")).when(session).close();
        when(client.connectAndSubscribe(anyList())).thenReturn(session);
        manager.connectAll(List.of("005930"));
        manager.closeOnShutdown();
        verify(session).close();
        assertThat(manager.isEmpty()).isTrue();
    }

    @Test
    void rejectsInvalidStockCodesBeforeCallingClient() {
        assertThatThrownBy(() -> manager.connectAll(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stockCodes are required");
        assertThatThrownBy(() -> manager.connectAll(Arrays.asList("005930", null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stockCode is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> manager.connectAll(List.of(" ")))
                .withMessage("stockCode must not be blank");
        verifyNoInteractions(client);
    }
}
