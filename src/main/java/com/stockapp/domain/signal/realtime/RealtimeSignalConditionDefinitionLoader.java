package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.screening.SearchConditionRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RealtimeSignalConditionDefinitionLoader {

    private final SearchConditionRepository searchConditionRepository;

    @Transactional(readOnly = true)
    public List<RealtimeSignalConditionDefinition> load(List<Long> conditionIds) {
        if (conditionIds == null || conditionIds.isEmpty()) {
            throw new IllegalArgumentException("conditionIds are required");
        }
        return searchConditionRepository
                .findAllByIdInAndEnabledTrueAndRealtimeEnabledTrueAndDeletedAtIsNull(
                        List.copyOf(conditionIds))
                .stream()
                .map(this::toDefinition)
                .toList();
    }

    private RealtimeSignalConditionDefinition toDefinition(
            SearchCondition condition) {
        List<RealtimeSignalRule> rules = condition.getRules().stream()
                .filter(rule -> rule.getStage() == ScreeningStage.SIGNAL)
                .map(this::toRule)
                .toList();
        return new RealtimeSignalConditionDefinition(condition.getId(), rules);
    }

    private RealtimeSignalRule toRule(SearchConditionRule rule) {
        return new RealtimeSignalRule(
                rule.getRuleOrder(),
                rule.getLogicalOperator(),
                rule.getLeftMetric(),
                rule.getLeftPeriod(),
                rule.getOperator(),
                rule.getRightType(),
                rule.getRightValue(),
                rule.getRightMetric(),
                rule.getRightPeriod());
    }
}
