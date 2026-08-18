package com.stockapp.external.kis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class KisWebSocketSessionManagerTest {

    @Mock
    private KisWebSocketClient client;

    @Mock
    private KisWebSocketSessionSleeper sleeper;

    private KisWebSocketSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new KisWebSocketSessionManager(client, sleeper);
    }

    @Test
    void acceptsEmptyTargetsWithoutCreatingSessions() {
        manager.connectAll(List.of());

        assertThat(manager.sessions()).isEmpty();
        assertThat(manager.sessionCount()).isZero();
        assertThat(manager.isEmpty()).isTrue();
        verifyNoInteractions(client, sleeper);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1",
            "39, 1",
            "40, 1",
            "41, 2",
            "79, 2",
            "80, 2",
            "81, 3",
            "323, 9"
    })
    void partitionsTargetsIntoAtMostFortyStocks(
            int stockCount,
            int expectedSessionCount
    ) throws Exception {
        when(client.connectAndSubscribe(anyList()))
                .thenAnswer(invocation -> mock(KisWebSocketSession.class));
        List<String> stockCodes = stockCodes(stockCount);

        manager.connectAll(stockCodes);

        ArgumentCaptor<List<String>> chunks = ArgumentCaptor.forClass(List.class);
        verify(client, times(expectedSessionCount))
                .connectAndSubscribe(chunks.capture());
        assertThat(chunks.getAllValues())
                .allSatisfy(chunk -> assertThat(chunk).hasSizeLessThanOrEqualTo(
                        KisWebSocketSessionManager.MAX_STOCKS_PER_SESSION));
        assertThat(chunks.getAllValues().stream()
                .flatMap(List::stream)
                .toList()).containsExactlyElementsOf(stockCodes);
        assertThat(manager.sessionCount()).isEqualTo(expectedSessionCount);
        assertThat(manager.sessions()).hasSize(expectedSessionCount);
        verify(sleeper, times(expectedSessionCount - 1)).sleep(1_000L);
    }

    @Test
    void removesDuplicatesBeforePartitionAndPreservesFirstOrder()
            throws Exception {
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(mock(KisWebSocketSession.class));
        List<String> input = new ArrayList<>(stockCodes(40));
        input.add(1, input.getFirst());

        manager.connectAll(input);

        ArgumentCaptor<List<String>> chunk = ArgumentCaptor.forClass(List.class);
        verify(client).connectAndSubscribe(chunk.capture());
        assertThat(chunk.getValue()).containsExactlyElementsOf(stockCodes(40));
        assertThat(manager.sessionCount()).isEqualTo(1);
        verifyNoInteractions(sleeper);
    }

    @Test
    void publishesSessionsOnlyAfterEveryConnectionSucceeds()
            throws Exception {
        when(client.connectAndSubscribe(anyList())).thenAnswer(invocation -> {
            assertThat(manager.sessions()).isEmpty();
            return mock(KisWebSocketSession.class);
        });

        manager.connectAll(stockCodes(81));

        assertThat(manager.sessions()).hasSize(3);
    }

    @Test
    void rollsBackConnectedSessionsWhenLaterConnectionFails()
            throws Exception {
        KisWebSocketSession first = mock(KisWebSocketSession.class);
        KisWebSocketSession second = mock(KisWebSocketSession.class);
        KisWebSocketException failure = new KisWebSocketException(
                "connect failed", new IOException("network failed"));
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(first, second)
                .thenThrow(failure);

        assertThatThrownBy(() -> manager.connectAll(stockCodes(81)))
                .isSameAs(failure);

        verify(first).close();
        verify(second).close();
        assertThat(manager.sessions()).isEmpty();
    }

    @Test
    void addsRollbackCloseFailuresToOriginalConnectionFailure()
            throws Exception {
        KisWebSocketSession first = mock(KisWebSocketSession.class);
        IOException closeFailure = new IOException("close failed");
        doThrow(closeFailure).when(first).close();
        KisWebSocketException connectionFailure = new KisWebSocketException(
                "connect failed", new IOException("network failed"));
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(first)
                .thenThrow(connectionFailure);

        assertThatThrownBy(() -> manager.connectAll(stockCodes(41)))
                .isSameAs(connectionFailure)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .containsExactly(closeFailure));
        assertThat(manager.sessions()).isEmpty();
    }

    @Test
    void rejectsConnectWhenSessionsAreAlreadyActive() throws Exception {
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(mock(KisWebSocketSession.class));
        manager.connectAll(List.of("005930"));

        assertThatIllegalStateException()
                .isThrownBy(() -> manager.connectAll(List.of("000660")))
                .withMessage("KIS WebSocket sessions are already active");

        verify(client).connectAndSubscribe(anyList());
    }

    @Test
    void closeAllClosesEverySessionAndClearsState() throws Exception {
        KisWebSocketSession first = mock(KisWebSocketSession.class);
        KisWebSocketSession second = mock(KisWebSocketSession.class);
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(first, second);
        manager.connectAll(stockCodes(41));

        manager.closeAll();

        verify(first).close();
        verify(second).close();
        assertThat(manager.sessions()).isEmpty();
    }

    @Test
    void closeAllContinuesAndAggregatesFailures() throws Exception {
        KisWebSocketSession first = mock(KisWebSocketSession.class);
        KisWebSocketSession second = mock(KisWebSocketSession.class);
        KisWebSocketSession third = mock(KisWebSocketSession.class);
        IOException firstFailure = new IOException("first close failed");
        IOException secondFailure = new IOException("second close failed");
        doThrow(firstFailure).when(first).close();
        doThrow(secondFailure).when(second).close();
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(first, second, third);
        manager.connectAll(stockCodes(81));

        assertThatThrownBy(manager::closeAll)
                .isSameAs(firstFailure)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .containsExactly(secondFailure));

        verify(third).close();
        assertThat(manager.sessions()).isEmpty();
    }

    @Test
    void closeAllIsNoOpWhenEmpty() throws Exception {
        manager.closeAll();

        assertThat(manager.sessions()).isEmpty();
        verifyNoInteractions(client, sleeper);
    }

    @Test
    void shutdownClosesSessionsWithoutPropagatingFailure() throws Exception {
        KisWebSocketSession session = mock(KisWebSocketSession.class);
        doThrow(new IOException("close failed")).when(session).close();
        when(client.connectAndSubscribe(anyList())).thenReturn(session);
        manager.connectAll(List.of("005930"));

        manager.closeOnShutdown();

        verify(session).close();
        assertThat(manager.sessions()).isEmpty();
    }

    @Test
    void rejectsInvalidStockCodesBeforeCallingClient() {
        assertThatThrownBy(() -> manager.connectAll(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stockCodes are required");
        assertThatThrownBy(() -> manager.connectAll(
                Arrays.asList("005930", null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stockCode is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> manager.connectAll(List.of(" ")))
                .withMessage("stockCode must not be blank");

        verifyNoInteractions(client, sleeper);
    }

    @Test
    void returnedSessionSnapshotIsImmutable() throws Exception {
        when(client.connectAndSubscribe(anyList()))
                .thenReturn(mock(KisWebSocketSession.class));
        manager.connectAll(List.of("005930"));

        assertThatThrownBy(() -> manager.sessions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private List<String> stockCodes(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "%06d".formatted(index))
                .toList();
    }
}
