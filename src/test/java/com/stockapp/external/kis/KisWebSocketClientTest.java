package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class KisWebSocketClientTest {

    @Mock
    private KisWebSocketApprovalClient approvalClient;

    @Mock
    private KisWebSocketHandler handler;

    @Mock
    private KisWebSocketConnector connector;

    @Mock
    private WebSocketSession webSocketSession;

    private KisWebSocketClient client;
    private KisWebSocketSubscriptionTracker subscriptionTracker;

    @BeforeEach
    void setUp() {
        KisProperties properties = new KisProperties();
        properties.setWebSocketUrl("ws://localhost/test");
        subscriptionTracker = new KisWebSocketSubscriptionTracker();
        client = new KisWebSocketClient(
                properties, approvalClient, handler, connector,
                subscriptionTracker);
    }

    @Test
    void connectsSubscribesAndReturnsManagedSession() throws Exception {
        when(approvalClient.getApprovalKey()).thenReturn("approval-key");
        when(connector.connect(
                same(handler), any(URI.class)))
                .thenReturn(webSocketSession);
        when(webSocketSession.getId()).thenReturn("session-1");
        doAnswer(invocation -> {
            subscriptionTracker.handle("session-1",
                    new KisWebSocketControlResponse(
                            "H0STCNT0", "005930", "0", null,
                            "SUBSCRIBE SUCCESS"));
            return null;
        }).when(webSocketSession).sendMessage(any(TextMessage.class));

        KisWebSocketSession session = client.connectAndSubscribe(
                List.of("005930", "005930"));

        assertThat(session.sessionId()).isEqualTo("session-1");
        assertThat(session.requestedStockCodes()).containsExactly("005930");
        assertThat(session.confirmedStockCodes()).containsExactly("005930");
        ArgumentCaptor<TextMessage> messageCaptor =
                ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload())
                .contains("\"approval_key\": \"approval-key\"")
                .contains("\"tr_key\": \"005930\"");
    }

    @Test
    void rejectsInvalidStockCodesBeforeCallingDependencies() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.connectAndSubscribe(List.of()))
                .withMessage("at least one stockCode is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.connectAndSubscribe(List.of(" ")))
                .withMessage("stockCode must not be blank");

        verifyNoInteractions(approvalClient, connector, webSocketSession);
    }

    @Test
    void buildsCurrentOfficialUnsubscribeTransactionType() {
        assertThat(client.createSubscriptionMessage(
                "approval-key", "005930", KisWebSocketOperation.UNSUBSCRIBE))
                .contains("\"tr_type\": \"0\"")
                .contains("\"tr_key\": \"005930\"");
    }

    @Test
    void propagatesApprovalAndConnectionFailures() throws Exception {
        RuntimeException approvalFailure = new IllegalStateException(
                "approval failed");
        when(approvalClient.getApprovalKey()).thenThrow(approvalFailure);

        assertThatThrownBy(() -> client.connectAndSubscribe("005930"))
                .isInstanceOf(KisWebSocketException.class)
                .hasMessage("KIS WebSocket approval key 조회에 실패했습니다.")
                .hasCause(approvalFailure);
        verifyNoInteractions(connector);

        org.mockito.Mockito.reset(approvalClient);
        when(approvalClient.getApprovalKey()).thenReturn("approval-key");
        IOException connectionFailure = new IOException("connect failed");
        when(connector.connect(same(handler), any(URI.class)))
                .thenThrow(connectionFailure);

        assertThatThrownBy(() -> client.connectAndSubscribe("005930"))
                .isInstanceOf(KisWebSocketException.class)
                .hasMessage("KIS WebSocket 연결에 실패했습니다.")
                .hasCause(connectionFailure);
    }

    @Test
    void closesSessionAndPropagatesSubscriptionFailure() throws Exception {
        when(approvalClient.getApprovalKey()).thenReturn("approval-key");
        when(connector.connect(same(handler), any(URI.class)))
                .thenReturn(webSocketSession);
        when(webSocketSession.getId()).thenReturn("session-1");
        when(webSocketSession.isOpen()).thenReturn(true);
        IOException subscriptionFailure = new IOException("send failed");
        org.mockito.Mockito.doThrow(subscriptionFailure)
                .when(webSocketSession).sendMessage(any(TextMessage.class));

        assertThatThrownBy(() -> client.connectAndSubscribe("005930"))
                .isInstanceOf(KisWebSocketException.class)
                .hasMessage("KIS WebSocket subscription failed")
                .hasCause(subscriptionFailure);
        verify(webSocketSession).close();
    }

    @Test
    void retainsCloseFailureAsSuppressedOnSubscriptionFailure()
            throws Exception {
        when(approvalClient.getApprovalKey()).thenReturn("approval-key");
        when(connector.connect(same(handler), any(URI.class)))
                .thenReturn(webSocketSession);
        when(webSocketSession.getId()).thenReturn("session-1");
        when(webSocketSession.isOpen()).thenReturn(true);
        IOException subscriptionFailure = new IOException("send failed");
        IOException closeFailure = new IOException("close failed");
        org.mockito.Mockito.doThrow(subscriptionFailure)
                .when(webSocketSession).sendMessage(any(TextMessage.class));
        org.mockito.Mockito.doThrow(closeFailure)
                .when(webSocketSession).close();

        assertThatThrownBy(() -> client.connectAndSubscribe("005930"))
                .isInstanceOf(KisWebSocketException.class)
                .satisfies(exception -> assertThat(exception.getCause()
                        .getSuppressed()).containsExactly(closeFailure));
    }

    @Test
    void stopsSendingAfterRejectedAck() throws Exception {
        when(approvalClient.getApprovalKey()).thenReturn("approval-key");
        when(connector.connect(same(handler), any(URI.class)))
                .thenReturn(webSocketSession);
        when(webSocketSession.getId()).thenReturn("session-1");
        when(webSocketSession.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            subscriptionTracker.handle("session-1",
                    new KisWebSocketControlResponse(
                            "H0STCNT0", "005930", "9", "OPSP8996",
                            "ALREADY IN USE appkey"));
            return null;
        }).when(webSocketSession).sendMessage(any(TextMessage.class));

        assertThatThrownBy(() -> client.connectAndSubscribe(
                List.of("005930", "000660")))
                .isInstanceOf(KisWebSocketException.class)
                .hasMessageContaining("OPSP8996")
                .satisfies(exception -> assertThat(
                        ((KisWebSocketException) exception)
                                .subscriptionResult().stockCode())
                        .isEqualTo("005930"));

        verify(webSocketSession, times(1))
                .sendMessage(any(TextMessage.class));
        verify(webSocketSession).close();
    }
}
