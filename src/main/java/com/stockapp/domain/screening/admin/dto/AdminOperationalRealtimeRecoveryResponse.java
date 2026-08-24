package com.stockapp.domain.screening.admin.dto;

import com.stockapp.domain.screening.realtime.OperationalMorningManualRetryStatus;
import com.stockapp.domain.screening.realtime.RealtimeTargetReconciliationStatus;

public record AdminOperationalRealtimeRecoveryResponse(
        OperationalMorningManualRetryStatus status,
        RealtimeTargetReconciliationStatus reconciliationStatus,
        Integer desiredCount,
        Integer physicalCount,
        Integer registryCount,
        String failureOperation,
        String failureStockCode,
        String failureMessage
) {}
