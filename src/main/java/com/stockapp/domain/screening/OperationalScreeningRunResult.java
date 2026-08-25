package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningRunResult;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record OperationalScreeningRunResult(
        OperationalScreeningRunStatus status,
        LocalDate today,
        Optional<LocalDate> evaluationDate,
        Optional<OperationalScreeningCompletenessResult> completeness,
        Optional<ScreeningRunResult> screeningResult
) {

    public OperationalScreeningRunResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(today, "today is required");
        Objects.requireNonNull(evaluationDate,
                "evaluationDate Optional is required");
        Objects.requireNonNull(completeness,
                "completeness Optional is required");
        Objects.requireNonNull(screeningResult,
                "screeningResult Optional is required");
        validateState(status, evaluationDate, completeness, screeningResult);
    }

    public static OperationalScreeningRunResult notTradingDay(
            LocalDate today) {
        return new OperationalScreeningRunResult(
                OperationalScreeningRunStatus.NOT_TRADING_DAY,
                today, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static OperationalScreeningRunResult finalizationNotReady(
            LocalDate today, LocalDate evaluationDate) {
        return new OperationalScreeningRunResult(
                OperationalScreeningRunStatus.FINALIZATION_NOT_READY,
                today, Optional.of(evaluationDate),
                Optional.empty(), Optional.empty());
    }

    public static OperationalScreeningRunResult historyBootstrapNotReady(
            LocalDate today, LocalDate evaluationDate) {
        return new OperationalScreeningRunResult(
                OperationalScreeningRunStatus.HISTORY_BOOTSTRAP_NOT_READY,
                today, Optional.of(evaluationDate),
                Optional.empty(), Optional.empty());
    }

    public static OperationalScreeningRunResult dataIncomplete(
            LocalDate today,
            LocalDate evaluationDate,
            OperationalScreeningCompletenessResult completeness) {
        return new OperationalScreeningRunResult(
                OperationalScreeningRunStatus.DATA_INCOMPLETE,
                today, Optional.of(evaluationDate),
                Optional.of(completeness), Optional.empty());
    }

    public static OperationalScreeningRunResult completed(
            LocalDate today,
            LocalDate evaluationDate,
            OperationalScreeningCompletenessResult completeness,
            ScreeningRunResult screeningResult) {
        return new OperationalScreeningRunResult(
                OperationalScreeningRunStatus.COMPLETED,
                today, Optional.of(evaluationDate),
                Optional.of(completeness), Optional.of(screeningResult));
    }

    private static void validateState(
            OperationalScreeningRunStatus status,
            Optional<LocalDate> evaluationDate,
            Optional<OperationalScreeningCompletenessResult> completeness,
            Optional<ScreeningRunResult> screeningResult
    ) {
        if (status == OperationalScreeningRunStatus.NOT_TRADING_DAY) {
            require(evaluationDate.isEmpty() && completeness.isEmpty()
                    && screeningResult.isEmpty());
            return;
        }
        require(evaluationDate.isPresent());
        if (status == OperationalScreeningRunStatus.FINALIZATION_NOT_READY
                || status == OperationalScreeningRunStatus
                .HISTORY_BOOTSTRAP_NOT_READY) {
            require(completeness.isEmpty() && screeningResult.isEmpty());
            return;
        }
        require(completeness.isPresent());
        require(evaluationDate.get().equals(
                completeness.get().evaluationDate()));
        if (status == OperationalScreeningRunStatus.DATA_INCOMPLETE) {
            require(completeness.get().status()
                    == OperationalScreeningCompletenessStatus.INCOMPLETE
                    && screeningResult.isEmpty());
            return;
        }
        require(completeness.get().status()
                == OperationalScreeningCompletenessStatus.COMPLETE
                && screeningResult.isPresent()
                && evaluationDate.get().equals(
                screeningResult.get().baseDate()));
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException(
                    "operational screening result state is inconsistent");
        }
    }
}
