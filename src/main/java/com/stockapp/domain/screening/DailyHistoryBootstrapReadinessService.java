package com.stockapp.domain.screening;

import com.stockapp.domain.stock.DailyHistoryBootstrapExecutionStore;
import com.stockapp.domain.stock.OperationalStockUniverseFingerprint;
import com.stockapp.domain.stock.OperationalStockUniverseService;
import com.stockapp.domain.stock.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyHistoryBootstrapReadinessService {

    private final DailyHistoryBootstrapExecutionStore executionStore;
    private final OperationalStockUniverseService stockUniverseService;
    private final OperationalStockUniverseFingerprint universeFingerprint;

    public DailyHistoryBootstrapReadinessResult check(
            LocalDate evaluationDate,
            int requiredPreviousTradingDayCount
    ) {
        List<Stock> targets = stockUniverseService.findHistoryTargets();
        if (targets.isEmpty()) {
            return new DailyHistoryBootstrapReadinessResult(
                    evaluationDate, requiredPreviousTradingDayCount,
                    Optional.empty());
        }
        String fingerprint = universeFingerprint.calculate(targets);
        return new DailyHistoryBootstrapReadinessResult(
                evaluationDate,
                requiredPreviousTradingDayCount,
                executionStore.findLatestReady(
                        evaluationDate,
                        requiredPreviousTradingDayCount,
                        fingerprint,
                        OperationalStockUniverseFingerprint.HISTORY_POLICY_VERSION));
    }
}
