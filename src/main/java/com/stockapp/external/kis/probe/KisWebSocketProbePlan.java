package com.stockapp.external.kis.probe;

import java.util.List;

public record KisWebSocketProbePlan(
        KisWebSocketProbeMode mode,
        List<String> initialStockCodes,
        String unsubscribeStockCode,
        String replacementStockCode
) {
    public KisWebSocketProbePlan {
        initialStockCodes = List.copyOf(initialStockCodes);
    }
}
