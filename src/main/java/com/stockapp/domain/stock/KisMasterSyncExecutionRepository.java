package com.stockapp.domain.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface KisMasterSyncExecutionRepository
        extends JpaRepository<KisMasterSyncExecution, Long> {

    Optional<KisMasterSyncExecution>
    findFirstByStatusOrderByFinishedAtDescIdDesc(
            KisMasterSyncExecutionStatus status);

    List<KisMasterSyncExecution> findByStatusOrderByStartedAtAsc(
            KisMasterSyncExecutionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<KisMasterSyncExecution> findWithLockByStatusOrderByStartedAtAsc(
            KisMasterSyncExecutionStatus status);
}
