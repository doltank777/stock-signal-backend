package com.stockapp.external.kis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockapp.domain.signal.realtime.RealtimeSignalConditionResult;
import com.stockapp.domain.signal.realtime.RealtimeSignalEvaluationResult;
import com.stockapp.domain.signal.realtime.RealtimeSignalPersistenceService;
import com.stockapp.domain.signal.realtime.RealtimeTradeSignalEvaluationService;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KisWebSocketHandlerTest {

    private static final String REALTIME_PAYLOAD = "0|H0STCNT0|1|trade";

    private KisRealtimeTradeParser parser;
    private RealtimeTradeSignalEvaluationService evaluationService;
    private RealtimeSignalPersistenceService persistenceService;
    private WebSocketSession session;
    private KisWebSocketHandler handler;
    private KisWebSocketSubscriptionTracker subscriptionTracker;

    @BeforeEach
    void setUp() {
        parser = mock(KisRealtimeTradeParser.class);
        evaluationService = mock(RealtimeTradeSignalEvaluationService.class);
        persistenceService = mock(RealtimeSignalPersistenceService.class);
        session = mock(WebSocketSession.class);
        subscriptionTracker = new KisWebSocketSubscriptionTracker();
        handler = new KisWebSocketHandler(
                parser,
                new KisWebSocketControlResponseParser(new ObjectMapper()),
                subscriptionTracker,
                evaluationService,
                persistenceService);
    }

    @Test
    void passesParsedRealtimeTradeToEvaluationServiceOnce() throws Exception {
        KisRealtimeTradePrice trade = trade();
        when(parser.supports(REALTIME_PAYLOAD)).thenReturn(true);
        when(parser.parse(REALTIME_PAYLOAD)).thenReturn(trade);
        when(evaluationService.evaluate(trade)).thenReturn(Optional.empty());

        handler.handleTextMessage(session, new TextMessage(REALTIME_PAYLOAD));

        verify(parser).parse(REALTIME_PAYLOAD);
        verify(evaluationService).evaluate(trade);
        verifyNoInteractions(persistenceService);
    }

    @Test
    void handlesNoMatchesAndMatchedConditionsWithoutSideEffects() throws Exception {
        KisRealtimeTradePrice trade = trade();
        when(parser.supports(REALTIME_PAYLOAD)).thenReturn(true);
        when(parser.parse(REALTIME_PAYLOAD)).thenReturn(trade);
        when(evaluationService.evaluate(trade))
                .thenReturn(Optional.of(result(false, false, false)))
                .thenReturn(Optional.of(result(false, true, true)));

        assertThatCode(() -> handler.handleTextMessage(
                session, new TextMessage(REALTIME_PAYLOAD)))
                .doesNotThrowAnyException();
        assertThatCode(() -> handler.handleTextMessage(
                session, new TextMessage(REALTIME_PAYLOAD)))
                .doesNotThrowAnyException();

        verify(evaluationService, org.mockito.Mockito.times(2)).evaluate(trade);
        verify(persistenceService).persistMatchedSignals(
                org.mockito.ArgumentMatchers.argThat(result ->
                        result.conditionResults().stream().noneMatch(
                                RealtimeSignalConditionResult::matched)));
        verify(persistenceService).persistMatchedSignals(
                org.mockito.ArgumentMatchers.argThat(result ->
                        result.conditionResults().stream().filter(
                                RealtimeSignalConditionResult::matched).count() == 2));
    }

    @Test
    void isolatesParserAndEvaluationFailuresToCurrentMessage() throws Exception {
        when(parser.supports(REALTIME_PAYLOAD)).thenReturn(true);
        when(parser.parse(REALTIME_PAYLOAD))
                .thenThrow(new IllegalArgumentException("invalid trade"));

        assertThatCode(() -> handler.handleTextMessage(
                session, new TextMessage(REALTIME_PAYLOAD)))
                .doesNotThrowAnyException();
        verifyNoInteractions(evaluationService);
        verifyNoInteractions(persistenceService);

        KisRealtimeTradePrice trade = trade();
        org.mockito.Mockito.doReturn(trade)
                .when(parser).parse(REALTIME_PAYLOAD);
        when(evaluationService.evaluate(trade))
                .thenThrow(new IllegalStateException("evaluation failed"));

        assertThatCode(() -> handler.handleTextMessage(
                session, new TextMessage(REALTIME_PAYLOAD)))
                .doesNotThrowAnyException();
        verifyNoInteractions(persistenceService);
    }

    @Test
    void isolatesPersistenceFailureToCurrentMessage() throws Exception {
        KisRealtimeTradePrice trade = trade();
        RealtimeSignalEvaluationResult result = result(false, true, false);
        when(parser.supports(REALTIME_PAYLOAD)).thenReturn(true);
        when(parser.parse(REALTIME_PAYLOAD)).thenReturn(trade);
        when(evaluationService.evaluate(trade)).thenReturn(Optional.of(result));
        org.mockito.Mockito.doThrow(new IllegalStateException("save failed"))
                .when(persistenceService).persistMatchedSignals(result);

        assertThatCode(() -> handler.handleTextMessage(
                session, new TextMessage(REALTIME_PAYLOAD)))
                .doesNotThrowAnyException();
        verify(persistenceService).persistMatchedSignals(result);
    }

    @Test
    void doesNotEvaluatePingPongJsonOrUnsupportedMessages() throws Exception {
        handler.handleTextMessage(session, new TextMessage("PINGPONG"));
        handler.handleTextMessage(session, new TextMessage("{\"header\":{}}"));
        handler.handleTextMessage(session, new TextMessage("unsupported"));

        verify(parser, never()).parse(org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(evaluationService);
        verifyNoInteractions(persistenceService);
    }

    @Test
    void correlatesControlAckAndFailsPendingOnCloseOrTransportError()
            throws Exception {
        when(session.getId()).thenReturn("session-1");
        subscriptionTracker.registerPending(
                "session-1", "H0STCNT0", "005930",
                KisWebSocketOperation.SUBSCRIBE);
        handler.handleTextMessage(session, new TextMessage("""
                {"header":{"tr_id":"H0STCNT0","tr_key":"005930"},
                 "body":{"rt_cd":"0","msg1":"SUBSCRIBE SUCCESS"}}
                """));
        assertThat(subscriptionTracker.snapshot("session-1").getFirst().status())
                .isEqualTo(KisSubscriptionStatus.CONFIRMED);

        subscriptionTracker.registerPending(
                "session-1", "H0STCNT0", "000660",
                KisWebSocketOperation.SUBSCRIBE);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertThat(subscriptionTracker.snapshot("session-1").stream()
                .filter(result -> result.stockCode().equals("000660"))
                .findFirst()).hasValueSatisfying(result ->
                assertThat(result.messageCode()).isEqualTo("CONNECTION_CLOSED"));

        subscriptionTracker.registerPending(
                "session-2", "H0STCNT0", "035420",
                KisWebSocketOperation.SUBSCRIBE);
        WebSocketSession second = mock(WebSocketSession.class);
        when(second.getId()).thenReturn("session-2");
        handler.handleTransportError(second, new IllegalStateException("broken"));
        assertThat(subscriptionTracker.snapshot("session-2").getFirst().messageCode())
                .isEqualTo("TRANSPORT_ERROR");
    }

    private KisRealtimeTradePrice trade() {
        return KisRealtimeTradePrice.builder()
                .stockCode("005930")
                .currentPrice(71_000L)
                .accumulatedVolume(2_500_000L)
                .tradeDateTime(LocalDateTime.of(2026, 8, 19, 10, 0))
                .build();
    }

    private RealtimeSignalEvaluationResult result(
            boolean third, boolean first, boolean second) {
        return new RealtimeSignalEvaluationResult(
                10L, "005930", LocalDateTime.of(2026, 8, 19, 10, 0),
                List.of(
                        new RealtimeSignalConditionResult(3L, third),
                        new RealtimeSignalConditionResult(1L, first),
                        new RealtimeSignalConditionResult(2L, second)));
    }
}
