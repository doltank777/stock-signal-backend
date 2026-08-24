package com.stockapp.domain.screening.admin.dto;

import com.stockapp.domain.screening.realtime.OperationalMorningRunStatus;
import com.stockapp.domain.stock.MarketType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

public record AdminOperationalRealtimeStatusResponse(
        ZonedDateTime currentKst,
        Session session,
        Automation automation,
        Morning morning,
        Screening screening,
        Desired desired,
        Applied applied
) {
    public record Session(
            boolean startupRecoveryWindow,
            boolean morningPreparationWindow,
            boolean regularMonitoringWindow,
            boolean deadlineReached
    ) {}

    public record Automation(
            boolean morningEnabled,
            LocalTime morningStart,
            LocalTime morningDeadline,
            Duration retryInterval,
            LocalTime marketOpen,
            LocalTime marketClose
    ) {}

    public record Morning(
            LocalDate date,
            OperationalMorningRunStatus status,
            int executionAttemptCount,
            ZonedDateTime lastAttemptAt,
            boolean pendingReconciliation,
            int pendingSelectedCount,
            String staleClearStatus,
            String failureOperation,
            String failureStockCode,
            String failureMessage
    ) {}

    public record Screening(
            boolean available,
            LocalDate evaluationDate,
            int candidateCount,
            boolean staleForCurrentDate
    ) {}

    public record Desired(
            boolean available,
            int capacity,
            int uniqueCandidateCount,
            int selectedCount,
            int excludedCount,
            List<Target> selectedTargets
    ) {}

    public record Target(
            int rank,
            Long stockId,
            String stockCode,
            String stockName,
            MarketType market,
            int effectivePriority,
            int effectiveScreeningScore,
            List<Condition> matchedConditions
    ) {}

    public record Condition(
            Long searchConditionId,
            String searchConditionName,
            int priority,
            int screeningScore
    ) {}

    public record Applied(
            int capacity,
            int registryCount,
            int physicalCount,
            int unmappedPhysicalCount,
            boolean registryPhysicalMismatch,
            boolean desiredApplied,
            List<String> desiredNotInRegistry,
            List<String> registryNotDesired,
            List<String> unmappedPhysicalStockCodes
    ) {}
}
