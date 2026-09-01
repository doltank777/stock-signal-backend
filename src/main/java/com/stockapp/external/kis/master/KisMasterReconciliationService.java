package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.KisMasterSyncExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KisMasterReconciliationService {

    private final KisMasterReconciliationPublisher publisher;
    private final KisMasterSyncExecutionFailureRecorder failureRecorder;

    public KisMasterReconciliationResult reconcile(
            KisMasterSnapshot kospiSnapshot,
            KisMasterSnapshot kosdaqSnapshot,
            KisMasterSyncExecution execution
    ) {
        Long executionId = execution == null ? null : execution.getId();
        try {
            KisMasterReconciliationResult result =
                    publisher.publish(kospiSnapshot, kosdaqSnapshot, executionId);
            log.info("KIS Master reconciliation completed: executionId={}, result={}",
                    executionId, result);
            return result;
        } catch (RuntimeException failure) {
            failureRecorder.record(executionId, failure);
            log.warn("KIS Master reconciliation failed: executionId={}, reason={}",
                    executionId, failure.getMessage());
            throw failure;
        }
    }
}
