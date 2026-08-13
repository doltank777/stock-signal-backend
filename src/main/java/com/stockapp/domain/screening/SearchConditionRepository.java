package com.stockapp.domain.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SearchConditionRepository
        extends JpaRepository<SearchCondition, Long> {

    List<SearchCondition> findAllByDeletedAtIsNullOrderByPriorityDescUpdatedAtDesc();

    List<SearchCondition> findAllByDeletedAtIsNotNullOrderByDeletedAtDesc();

    Optional<SearchCondition> findByIdAndDeletedAtIsNull(Long id);

    Optional<SearchCondition> findByIdAndDeletedAtIsNotNull(Long id);
}
