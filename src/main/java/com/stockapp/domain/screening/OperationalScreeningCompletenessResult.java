package com.stockapp.domain.screening;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record OperationalScreeningCompletenessResult(
        LocalDate evaluationDate,
        OperationalScreeningCompletenessStatus status,
        int targetStockCount,
        int availableStockCount,
        int missingStockCount,
        List<OperationalScreeningMissingStock> missingStocks
) {

    public OperationalScreeningCompletenessResult {
        Objects.requireNonNull(evaluationDate, "evaluationDate is required");
        Objects.requireNonNull(status, "status is required");
        missingStocks = List.copyOf(Objects.requireNonNull(
                missingStocks, "missingStocks is required"));
        if (targetStockCount < 0 || availableStockCount < 0
                || missingStockCount < 0) {
            throw new IllegalArgumentException("stock counts must not be negative");
        }
        if (availableStockCount + missingStockCount != targetStockCount
                || missingStockCount != missingStocks.size()) {
            throw new IllegalArgumentException("stock counts are inconsistent");
        }
        boolean complete = targetStockCount > 0 && missingStockCount == 0;
        if (complete != (status
                == OperationalScreeningCompletenessStatus.COMPLETE)) {
            throw new IllegalArgumentException(
                    "status does not match stock completeness");
        }
    }
}
