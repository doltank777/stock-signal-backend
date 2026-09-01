package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.KisMasterSyncExecution;
import com.stockapp.domain.stock.KisMasterSyncExecutionRepository;
import com.stockapp.domain.stock.KisMasterSyncExecutionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class KisMasterSyncExecutionFailureRecorder {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final KisMasterSyncExecutionRepository executionRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long executionId, RuntimeException failure) {
        if (executionId == null) {
            return;
        }
        executionRepository.findById(executionId).ifPresent(execution -> {
            if (execution.getStatus() == KisMasterSyncExecutionStatus.RUNNING) {
                execution.fail(errorMessage(failure), Instant.now(clock));
            }
        });
    }

    private String errorMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
