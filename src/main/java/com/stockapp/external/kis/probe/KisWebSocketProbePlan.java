package com.stockapp.external.kis.probe;

import java.util.List;

public record KisWebSocketProbePlan(
        KisWebSocketProbeMode mode,
        List<String> initialStockCodes,
        String unsubscribeStockCode,
        String replacementStockCode,
        List<String> sessionAStockCodes,
        List<String> sessionBStockCodes
) {
    public KisWebSocketProbePlan {
        initialStockCodes = initialStockCodes == null
                ? List.of() : List.copyOf(initialStockCodes);
        sessionAStockCodes = sessionAStockCodes == null
                ? List.of() : List.copyOf(sessionAStockCodes);
        sessionBStockCodes = sessionBStockCodes == null
                ? List.of() : List.copyOf(sessionBStockCodes);
    }
}
