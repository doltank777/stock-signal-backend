package com.stockapp.domain.stock.dto;

import java.time.Instant;
import java.time.LocalDate;

public record KrxTradingCalendarSyncResult(
        LocalDate baseDate,
        int receivedCount,
        int insertedCount,
        int updatedCount,
        int unchangedCount,
        Instant startedAt,
        Instant finishedAt
) {
}
