package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.screening.ScreeningRunService;
import com.stockapp.domain.screening.LatestScreeningSnapshotRegistry;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import com.stockapp.external.kis.KisWebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeScreeningSubscriptionService {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final ScreeningRunService screeningRunService;
    private final LatestScreeningSnapshotRegistry screeningSnapshotRegistry;
    private final RealtimeScreeningBaseDateProvider baseDateProvider;
    private final RealtimeWatchTargetBuilder targetBuilder;
    private final RealtimeWatchTargetRegistry targetRegistry;
    private final KisWebSocketSessionManager sessionManager;

    public void initialize() {
        validateInitialState();

        List<Stock> stocks = stockRepository
                .findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
        if (stocks.isEmpty()) {
            throw new IllegalStateException(
                    "no KOSPI/KOSDAQ stocks found for realtime screening");
        }

        LocalDate baseDate = baseDateProvider.latestBaseDate();
        ScreeningRunResult result = screeningRunService.run(stocks, baseDate);
        screeningSnapshotRegistry.replace(result);
        List<RealtimeWatchTarget> targets = targetBuilder.build(result);
        List<String> stockCodes = targets.stream()
                .map(RealtimeWatchTarget::stockCode)
                .toList();

        log.info("realtime screening completed - baseDate: {}, "
                        + "candidateCount: {}, watchTargetCount: {}",
                baseDate, result.candidateStockCount(), targets.size());

        sessionManager.connectAll(stockCodes);
        try {
            targetRegistry.replace(targets);
        } catch (RuntimeException registryFailure) {
            closeSessionsAfterRegistryFailure(registryFailure);
            throw registryFailure;
        }

        log.info("realtime websocket initialization completed - "
                        + "targetCount: {}, sessionCount: {}",
                targets.size(), sessionManager.sessionCount());
    }

    private void validateInitialState() {
        if (!targetRegistry.isEmpty()) {
            throw new IllegalStateException(
                    "realtime watch target registry is already initialized");
        }
        if (!sessionManager.isEmpty()) {
            throw new IllegalStateException(
                    "KIS WebSocket sessions are already active");
        }
    }

    private void closeSessionsAfterRegistryFailure(
            RuntimeException registryFailure
    ) {
        try {
            sessionManager.closeAll();
        } catch (IOException closeFailure) {
            registryFailure.addSuppressed(closeFailure);
        }
    }
}
