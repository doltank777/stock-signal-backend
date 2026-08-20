package com.stockapp.domain.screening.admin.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminScreeningResultsResponse(
        boolean available,
        LocalDate baseDate,
        List<AdminScreeningConditionResultResponse> conditions
) {
    public AdminScreeningResultsResponse {
        conditions = List.copyOf(conditions);
    }

    public static AdminScreeningResultsResponse empty() {
        return new AdminScreeningResultsResponse(false, null, List.of());
    }
}
