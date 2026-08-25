package com.stockapp.domain.screening;

import com.stockapp.domain.stock.DailyHistoryBootstrapExecutionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyHistoryBootstrapReadinessService {

    private final DailyHistoryBootstrapExecutionStore executionStore;

    public DailyHistoryBootstrapReadinessResult check(
            LocalDate evaluationDate,
            int requiredPreviousTradingDayCount
    ) {
        return new DailyHistoryBootstrapReadinessResult(
                evaluationDate,
                requiredPreviousTradingDayCount,
                executionStore.findLatestReady(
                        evaluationDate,
                        requiredPreviousTradingDayCount));
    }
}
