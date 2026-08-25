package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.BootstrapDailyHistoryBatchResult;

public class DailyHistoryBootstrapNotReadyException extends RuntimeException {

    private final BootstrapDailyHistoryBatchResult result;

    public DailyHistoryBootstrapNotReadyException(
            BootstrapDailyHistoryBatchResult result
    ) {
        super("daily history bootstrap completed with remaining gaps");
        this.result = result;
    }

    public BootstrapDailyHistoryBatchResult getResult() {
        return result;
    }
}
