package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyHistoryBootstrapExecutionRepository
        extends JpaRepository<DailyHistoryBootstrapExecution, Long> {

    List<DailyHistoryBootstrapExecution>
    findByEvaluationDateOrderByFinishedAtDescIdDesc(LocalDate evaluationDate);

    Optional<DailyHistoryBootstrapExecution>
    findFirstByEvaluationDateOrderByFinishedAtDescIdDesc(
            LocalDate evaluationDate);

    Optional<DailyHistoryBootstrapExecution>
    findFirstByEvaluationDateAndReadyTrueAndRequiredPreviousTradingDayCountGreaterThanEqualAndTargetUniverseFingerprintAndTargetUniversePolicyVersionOrderByFinishedAtDescIdDesc(
            LocalDate evaluationDate,
            int requiredPreviousTradingDayCount,
            String targetUniverseFingerprint,
            String targetUniversePolicyVersion);
}
