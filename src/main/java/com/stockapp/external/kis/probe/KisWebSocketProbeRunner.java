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
        if (plan.mode() == KisWebSocketProbeMode.MULTI_SESSION) {
            return executeMultiSession(plan);
        }
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

    private KisWebSocketProbeExecution executeMultiSession(
            KisWebSocketProbePlan plan
    ) throws IOException {
        KisWebSocketSession sessionA = null;
        KisWebSocketSession sessionB = null;
        RuntimeException primaryFailure = null;
        try {
            sessionA = webSocketClient.connectAndSubscribe(
                    plan.sessionAStockCodes());
            KisWebSocketProbeSummary summaryA = summary(
                    plan.sessionAStockCodes(), sessionA);
            logSessionSummary("A", summaryA, sessionA.isOpen());
            if (!sessionA.isOpen()) {
                throw new IllegalStateException(
                        "KIS WebSocket probe session A closed before session B connection");
            }

            sessionB = webSocketClient.connectAndSubscribe(
                    plan.sessionBStockCodes());
            KisWebSocketProbeSummary summaryB = summary(
                    plan.sessionBStockCodes(), sessionB);
            boolean sessionAOpen = sessionA.isOpen();
            boolean sessionBOpen = sessionB.isOpen();
            boolean bothOpen = sessionAOpen && sessionBOpen;
            logSessionSummary("B", summaryB, sessionBOpen);
            log.info("KIS WebSocket multi-session probe completed - "
                            + "sessionAOpen: {}, sessionBOpen: {}, bothOpen: {}",
                    sessionAOpen, sessionBOpen, bothOpen);
            if (!bothOpen) {
                throw new IllegalStateException(
                        "both KIS WebSocket probe sessions must remain open");
            }
            return new KisWebSocketProbeExecution(
                    summaryA, null, null,
                    sessionA.activeStockCodes().size(),
                    sessionB.activeStockCodes().size(),
                    summaryB, sessionAOpen, sessionBOpen, true);
        } catch (KisWebSocketException exception) {
            primaryFailure = exception;
            logMultiSessionFailure(plan.sessionBStockCodes(), sessionA, exception);
            throw exception;
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            closeMultiSession(sessionB, sessionA, primaryFailure);
        }
    }

    private KisWebSocketProbeSummary summary(
            List<String> requestedStockCodes,
            KisWebSocketSession session
    ) {
        return new KisWebSocketProbeSummary(
                requestedStockCodes, session.subscriptionResults());
    }

    private void logSessionSummary(
            String label,
            KisWebSocketProbeSummary summary,
            boolean open
    ) {
        log.info("KIS WebSocket multi-session probe session {} - "
                        + "requestedCount: {}, confirmedCount: {}, failedCount: {}, open: {}",
                label, summary.requestedStockCodes().size(),
                summary.confirmedCount(), summary.failedCount(), open);
    }

    private void logMultiSessionFailure(
            List<String> requestedStockCodes,
            KisWebSocketSession sessionA,
            KisWebSocketException exception
    ) {
        KisWebSocketSubscriptionResult failure = exception.subscriptionResult();
        log.error("KIS WebSocket multi-session probe session B failed - "
                        + "requestedCount: {}, sessionAOpen: {}, stockCode: {}, trId: {}, "
                        + "messageCode: {}, message: {}",
                requestedStockCodes.size(), sessionA != null && sessionA.isOpen(),
                failure == null ? null : failure.stockCode(),
                failure == null ? null : failure.trId(),
                failure == null ? null : failure.messageCode(),
                failure == null ? exception.getMessage() : failure.message());
    }

    private void closeMultiSession(
            KisWebSocketSession sessionB,
            KisWebSocketSession sessionA,
            RuntimeException primaryFailure
    ) throws IOException {
        IOException closeFailure = null;
        for (KisWebSocketSession session
                : new KisWebSocketSession[]{sessionB, sessionA}) {
            if (session == null) {
                continue;
            }
            try {
                session.close();
            } catch (IOException exception) {
                if (closeFailure == null) {
                    closeFailure = exception;
                } else {
                    closeFailure.addSuppressed(exception);
                }
            }
        }
        if (closeFailure != null) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(closeFailure);
            } else {
                throw closeFailure;
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
