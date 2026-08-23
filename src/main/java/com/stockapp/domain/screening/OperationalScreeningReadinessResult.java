package com.stockapp.domain.screening;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record OperationalScreeningReadinessResult(
        OperationalScreeningReadinessStatus status,
        LocalDate today,
        Optional<LocalDate> expectedEvaluationDate
) {

    public OperationalScreeningReadinessResult {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(today, "today is required");
        Objects.requireNonNull(expectedEvaluationDate,
                "expectedEvaluationDate Optional is required");
        if (status == OperationalScreeningReadinessStatus.NOT_TRADING_DAY
                && expectedEvaluationDate.isPresent()) {
            throw new IllegalArgumentException(
                    "not-trading-day result cannot have an evaluation date");
        }
        if (status != OperationalScreeningReadinessStatus.NOT_TRADING_DAY
                && expectedEvaluationDate.isEmpty()) {
            throw new IllegalArgumentException(
                    "trading-day result requires an evaluation date");
        }
    }

    public static OperationalScreeningReadinessResult notTradingDay(
            LocalDate today) {
        return new OperationalScreeningReadinessResult(
                OperationalScreeningReadinessStatus.NOT_TRADING_DAY,
                today, Optional.empty());
    }

    public static OperationalScreeningReadinessResult finalizationNotReady(
            LocalDate today, LocalDate expectedEvaluationDate) {
        return new OperationalScreeningReadinessResult(
                OperationalScreeningReadinessStatus.FINALIZATION_NOT_READY,
                today, Optional.of(expectedEvaluationDate));
    }

    public static OperationalScreeningReadinessResult ready(
            LocalDate today, LocalDate expectedEvaluationDate) {
        return new OperationalScreeningReadinessResult(
                OperationalScreeningReadinessStatus.READY,
                today, Optional.of(expectedEvaluationDate));
    }
}
