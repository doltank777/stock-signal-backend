package com.stockapp.domain.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KisMasterSyncExecutionRepository
        extends JpaRepository<KisMasterSyncExecution, Long> {

    Optional<KisMasterSyncExecution>
    findFirstByStatusOrderByFinishedAtDescIdDesc(
            KisMasterSyncExecutionStatus status);

    List<KisMasterSyncExecution> findByStatusOrderByStartedAtAsc(
            KisMasterSyncExecutionStatus status);
}
