package com.stockapp.external.kis.probe;

import com.stockapp.external.kis.KisWebSocketClient;
import com.stockapp.external.kis.KisWebSocketException;
import com.stockapp.external.kis.KisWebSocketSession;
import com.stockapp.external.kis.KisWebSocketSubscriptionResult;
import com.stockapp.external.kis.KisWebSocketSubscriptionTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@Profile("kis-websocket-probe")
@RequiredArgsConstructor
public class KisWebSocketProbeRunner implements ApplicationRunner {

    private final KisWebSocketProbeProperties probeProperties;
    private final KisWebSocketClient webSocketClient;
    private final KisWebSocketSubscriptionTracker subscriptionTracker;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        execute();
    }

    KisWebSocketProbeExecution execute() throws IOException {
        KisWebSocketProbePlan plan = probeProperties.plan();
        List<String> requestedStockCodes = plan.initialStockCodes();
        KisWebSocketSession session = null;
        try {
            session = webSocketClient.connectAndSubscribe(requestedStockCodes);
            KisWebSocketProbeSummary summary = new KisWebSocketProbeSummary(
                    requestedStockCodes, session.subscriptionResults());
            logSummary(summary);
            if (plan.mode() == KisWebSocketProbeMode.SUBSCRIBE) {
                int activeCount = session.activeStockCodes().size();
                return new KisWebSocketProbeExecution(
                        summary, null, null, activeCount, activeCount);
            }

            KisWebSocketSubscriptionResult unsubscribeResult =
                    webSocketClient.unsubscribe(
                            session, plan.unsubscribeStockCode());
            int activeAfterUnsubscribe = session.activeStockCodes().size();
            logOperation("unsubscribe", unsubscribeResult,
                    activeAfterUnsubscribe);

            KisWebSocketSubscriptionResult replacementResult =
                    webSocketClient.subscribe(
                            session, plan.replacementStockCode());
            int activeAfterReplacement = session.activeStockCodes().size();
            logOperation("replacement", replacementResult,
                    activeAfterReplacement);
            return new KisWebSocketProbeExecution(
                    summary, unsubscribeResult, replacementResult,
                    activeAfterUnsubscribe, activeAfterReplacement);
        } catch (KisWebSocketException exception) {
            logFailure(requestedStockCodes, exception);
            throw exception;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private void logOperation(
            String phase,
            KisWebSocketSubscriptionResult result,
            int activeCount
    ) {
        log.info("KIS WebSocket probe {} - stockCode: {}, status: {}, "
                        + "messageCode: {}, message: {}, activeCount: {}",
                phase, result.stockCode(), result.status(),
                result.messageCode(), result.message(), activeCount);
    }

    private void logFailure(
            List<String> requestedStockCodes,
            KisWebSocketException exception
    ) {
        KisWebSocketSubscriptionResult failure = exception.subscriptionResult();
        if (failure == null) {
            log.error("KIS WebSocket probe failed before subscription result - requestedCount: {}",
                    requestedStockCodes.size(), exception);
            return;
        }
        KisWebSocketProbeSummary summary = new KisWebSocketProbeSummary(
                requestedStockCodes,
                subscriptionTracker.snapshot(failure.sessionId()));
        log.error("KIS WebSocket probe rejected - requestedCount: {}, confirmedCount: {}, "
                        + "failedCount: {}, operation: {}, firstFailedStockCode: {}, trId: {}, "
                        + "messageCode: {}, message: {}, activeCount: {}",
                requestedStockCodes.size(), summary.confirmedCount(), summary.failedCount(),
                failure.operation(), failure.stockCode(), failure.trId(),
                failure.messageCode(), failure.message(),
                subscriptionTracker.activeStockCodes(failure.sessionId()).size());
    }

    private void logSummary(KisWebSocketProbeSummary summary) {
        log.info("KIS WebSocket probe completed - requestedCount: {}, confirmedCount: {}, failedCount: {}",
                summary.requestedStockCodes().size(),
                summary.confirmedCount(), summary.failedCount());
        log.debug("KIS WebSocket probe stocks - requested: {}, confirmed: {}, failed: {}",
                summary.requestedStockCodes(),
                summary.confirmedStockCodes(), summary.failedStockCodes());
    }
}
