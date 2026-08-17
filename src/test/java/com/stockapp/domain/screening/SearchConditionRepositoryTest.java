package com.stockapp.domain.screening;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SearchConditionRepositoryTest {

    @Autowired
    private SearchConditionRepository searchConditionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsOnlyEnabledAndNotDeletedConditions() {
        SearchCondition executable = saveCondition(
                "executable", true, 100, 80, false);
        saveCondition("disabled", false, 400, 90, false);

        SearchCondition deletedEnabled = saveCondition(
                "deleted-enabled", true, 300, 70, true);
        deletedEnabled.softDelete(null);
        deletedEnabled.changeEnabled(true);

        SearchCondition deletedDisabled = saveCondition(
                "deleted-disabled", false, 200, 60, false);
        deletedDisabled.softDelete(null);
        searchConditionRepository.flush();
        entityManager.clear();

        List<SearchCondition> conditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();

        assertThat(conditions)
                .extracting(SearchCondition::getName)
                .containsExactly(executable.getName());
    }

    @Test
    void ordersMultipleExecutableConditionsByPriorityDescending() {
        saveCondition("priority-100", true, 100, 10, false);
        saveCondition("priority-300", true, 300, 90, true);
        saveCondition("priority-200", true, 200, 50, false);
        entityManager.clear();

        List<SearchCondition> conditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();

        assertThat(conditions)
                .extracting(SearchCondition::getName)
                .containsExactly("priority-300", "priority-200", "priority-100");
    }

    @Test
    void includesExecutableConditionsRegardlessOfRealtimeAndScreeningScore() {
        saveCondition("batch-low-score", true, 200, 0, false);
        saveCondition("realtime-high-score", true, 100, 100, true);
        entityManager.clear();

        List<SearchCondition> conditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();

        assertThat(conditions)
                .extracting(SearchCondition::getName)
                .containsExactly("batch-low-score", "realtime-high-score");
    }

    @Test
    void restoredConditionRemainsExcludedUntilEnabled() {
        SearchCondition condition = saveCondition(
                "restored", true, 100, 80, false);
        condition.softDelete(null);
        condition.restore();
        searchConditionRepository.flush();
        entityManager.clear();

        assertThat(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .isEmpty();
    }

    @Test
    void returnsEmptyListWhenExecutableConditionDoesNotExist() {
        saveCondition("disabled", false, 100, 80, false);
        entityManager.clear();

        assertThat(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .isEmpty();
    }

    @Test
    void fetchesRulesWithoutDuplicateConditionsAndKeepsRuleOrder() {
        SearchCondition first = createCondition(
                "first", true, 200, 80, true);
        first.addRule(rule(ScreeningStage.SCREENING, 2));
        first.addRule(rule(ScreeningStage.SCREENING, 1));
        first.addRule(rule(ScreeningStage.SIGNAL, 1));
        searchConditionRepository.save(first);

        SearchCondition second = createCondition(
                "second", true, 100, 80, false);
        second.addRule(rule(ScreeningStage.SCREENING, 1));
        searchConditionRepository.saveAndFlush(second);
        entityManager.clear();

        List<SearchCondition> conditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();

        assertThat(conditions)
                .extracting(SearchCondition::getName)
                .containsExactly("first", "second");
        assertThat(conditions).allSatisfy(condition ->
                assertThat(Hibernate.isInitialized(condition.getRules())).isTrue());
        assertThat(conditions.getFirst().getRules())
                .extracting(SearchConditionRule::getRuleOrder)
                .containsExactly(1, 1, 2);
    }

    private SearchCondition saveCondition(
            String name,
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled
    ) {
        SearchCondition condition = createCondition(
                name, enabled, priority, screeningScore, realtimeEnabled);
        condition.addRule(rule(ScreeningStage.SCREENING, 1));
        return searchConditionRepository.saveAndFlush(condition);
    }

    private SearchCondition createCondition(
            String name,
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled
    ) {
        return SearchCondition.create(
                name, null, enabled, priority,
                screeningScore, realtimeEnabled, null);
    }

    private SearchConditionRule rule(
            ScreeningStage stage,
            int ruleOrder
    ) {
        return SearchConditionRule.createValueRule(
                stage,
                ScreeningMetric.CURRENT_PRICE,
                null,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ONE,
                null,
                ruleOrder);
    }
}
