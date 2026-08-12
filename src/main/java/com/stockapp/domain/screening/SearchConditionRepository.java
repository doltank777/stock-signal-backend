package com.stockapp.domain.screening;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SearchConditionRepository
        extends JpaRepository<SearchCondition, Long> {

    List<SearchCondition> findAllByOrderByPriorityDescUpdatedAtDesc();
}