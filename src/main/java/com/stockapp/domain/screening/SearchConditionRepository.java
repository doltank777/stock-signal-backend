package com.stockapp.domain.screening;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface SearchConditionRepository
        extends JpaRepository<SearchCondition, Long> {

    List<SearchCondition> findAllByDeletedAtIsNullOrderByPriorityDescUpdatedAtDesc();

    List<SearchCondition> findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();

    @EntityGraph(attributePaths = "rules")
    List<SearchCondition>
            findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();

    @EntityGraph(attributePaths = "rules")
    List<SearchCondition> findAllByIdInAndEnabledTrueAndRealtimeEnabledTrueAndDeletedAtIsNull(
            List<Long> ids);

    Optional<SearchCondition> findByIdAndDeletedAtIsNull(Long id);

    Optional<SearchCondition> findByIdAndDeletedAtIsNotNull(Long id);
}
