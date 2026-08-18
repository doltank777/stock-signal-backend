package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.ScreeningRunService;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import com.stockapp.external.kis.KisWebSocketException;
import com.stockapp.external.kis.KisWebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeScreeningSubscriptionServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 13);
    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ScreeningRunService screeningRunService;

    @Mock
    private RealtimeScreeningBaseDateProvider baseDateProvider;

    @Mock
    private RealtimeWatchTargetBuilder targetBuilder;

    @Mock
    private RealtimeWatchTargetRegistry targetRegistry;

    @Mock
    private KisWebSocketSessionManager sessionManager;

    private RealtimeScreeningSubscriptionService service;
    private List<Stock> stocks;
    private ScreeningRunResult result;

    @BeforeEach
    void setUp() {
        service = new RealtimeScreeningSubscriptionService(
                stockRepository,
                screeningRunService,
                baseDateProvider,
                targetBuilder,
                targetRegistry,
                sessionManager);
        stocks = List.of(
                stock(1L, "0088M0"),
                stock(2L, "012210"),
                stock(3L, "478340"));
        result = new ScreeningRunResult(
                BASE_DATE,
                Instant.parse("2026-08-13T00:00:00Z"),
                Instant.parse("2026-08-13T00:00:01Z"),
                stocks.size(),
                stocks.size(),
                List.of(),
                List.of());
        lenient().when(targetRegistry.isEmpty()).thenReturn(true);
        lenient().when(sessionManager.isEmpty()).thenReturn(true);
    }

    @Test
    void screensAllMarketStocksConnectsInOrderThenPublishesRegistry() {
        prepareScreening();
        List<RealtimeWatchTarget> targets = List.of(
                target(1L, "0088M0", 1L),
                target(2L, "012210", 1L, 2L),
                target(3L, "478340", 3L, 1L, 2L));
        when(targetBuilder.build(result)).thenReturn(targets);
        when(sessionManager.sessionCount()).thenReturn(3);

        service.initialize();

        verify(stockRepository).findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        verify(screeningRunService).run(stocks, BASE_DATE);
        verify(targetBuilder).build(result);
        InOrder publicationOrder = inOrder(sessionManager, targetRegistry);
        publicationOrder.verify(sessionManager).connectAll(
                List.of("0088M0", "012210", "478340"));
        publicationOrder.verify(targetRegistry).replace(targets);
    }

    @Test
    void publishesEmptyRegistryWhenNoRealtimeTargetsExist() {
        prepareScreening();
        when(targetBuilder.build(result)).thenReturn(List.of());

        service.initialize();

        verify(sessionManager).connectAll(List.of());
        verify(targetRegistry).replace(List.of());
    }

    @Test
    void propagatesScreeningFailureWithoutChangingRuntimeState() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(stocks);
        when(baseDateProvider.latestBaseDate()).thenReturn(BASE_DATE);
        RuntimeException failure = new IllegalStateException(
                "screening failed");
        when(screeningRunService.run(stocks, BASE_DATE)).thenThrow(failure);

        assertThatThrownBy(service::initialize).isSameAs(failure);

        verifyNoInteractions(targetBuilder);
        verify(sessionManager, never()).connectAll(anyList());
        verify(targetRegistry, never()).replace(anyList());
    }

    @Test
    void propagatesConnectionFailureWithoutPublishingRegistry() {
        prepareScreening();
        List<RealtimeWatchTarget> targets = List.of(
                target(1L, "0088M0", 1L));
        when(targetBuilder.build(result)).thenReturn(targets);
        KisWebSocketException failure = new KisWebSocketException(
                "connect failed", new IOException("network failed"));
        doThrow(failure).when(sessionManager)
                .connectAll(List.of("0088M0"));

        assertThatThrownBy(service::initialize).isSameAs(failure);

        verify(targetRegistry, never()).replace(anyList());
    }

    @Test
    void closesSessionsWhenRegistryPublicationFails() throws Exception {
        prepareScreening();
        List<RealtimeWatchTarget> targets = List.of(
                target(1L, "0088M0", 1L));
        when(targetBuilder.build(result)).thenReturn(targets);
        RuntimeException registryFailure = new IllegalArgumentException(
                "registry failed");
        doThrow(registryFailure).when(targetRegistry).replace(targets);

        assertThatThrownBy(service::initialize).isSameAs(registryFailure);

        verify(sessionManager).closeAll();
    }

    @Test
    void retainsCloseFailureWhenRegistryPublicationFails() throws Exception {
        prepareScreening();
        List<RealtimeWatchTarget> targets = List.of(
                target(1L, "0088M0", 1L));
        when(targetBuilder.build(result)).thenReturn(targets);
        RuntimeException registryFailure = new IllegalArgumentException(
                "registry failed");
        IOException closeFailure = new IOException("close failed");
        doThrow(registryFailure).when(targetRegistry).replace(targets);
        doThrow(closeFailure).when(sessionManager).closeAll();

        assertThatThrownBy(service::initialize)
                .isSameAs(registryFailure)
                .satisfies(exception -> org.assertj.core.api.Assertions
                        .assertThat(exception.getSuppressed())
                        .containsExactly(closeFailure));
    }

    @Test
    void rejectsMissingAllMarketStocks() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(List.of());

        assertThatIllegalStateException()
                .isThrownBy(service::initialize)
                .withMessage(
                        "no KOSPI/KOSDAQ stocks found for realtime screening");

        verifyNoInteractions(baseDateProvider, screeningRunService, targetBuilder);
    }

    @Test
    void rejectsInitializationWhenRuntimeStateAlreadyExists() {
        when(targetRegistry.isEmpty()).thenReturn(false);

        assertThatIllegalStateException()
                .isThrownBy(service::initialize)
                .withMessage(
                        "realtime watch target registry is already initialized");

        verifyNoInteractions(stockRepository, baseDateProvider,
                screeningRunService, targetBuilder);
        verify(sessionManager, never()).connectAll(anyList());
    }

    private void prepareScreening() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(stocks);
        when(baseDateProvider.latestBaseDate()).thenReturn(BASE_DATE);
        when(screeningRunService.run(stocks, BASE_DATE)).thenReturn(result);
    }

    private RealtimeWatchTarget target(
            Long stockId,
            String stockCode,
            Long... conditionIds
    ) {
        return new RealtimeWatchTarget(
                stockId, stockCode, List.of(conditionIds));
    }

    private Stock stock(Long id, String stockCode) {
        return Stock.builder()
                .id(id)
                .stockCode(stockCode)
                .stockName("stock-" + stockCode)
                .marketType(MarketType.KOSPI)
                .build();
    }
}
