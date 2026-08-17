package com.stockapp.domain.screening.rule;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.screening.metric.StockMetricContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScreeningConditionEvaluator {

    private final ScreeningRuleEvaluator screeningRuleEvaluator;

    public boolean evaluate(
            SearchCondition condition,
            StockMetricContext context
    ) {
        if (condition == null) {
            throw new IllegalArgumentException("condition is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        List<SearchConditionRule> rules = condition.getRules().stream()
                .filter(rule -> rule.getStage() == ScreeningStage.SCREENING)
                .sorted(Comparator.comparingInt(SearchConditionRule::getRuleOrder))
                .toList();

        validateStructure(rules);

        boolean result = screeningRuleEvaluator.evaluate(rules.getFirst(), context);
        for (int index = 1; index < rules.size(); index++) {
            SearchConditionRule rule = rules.get(index);
            ScreeningLogicalOperator logicalOperator = rule.getLogicalOperator();

            switch (logicalOperator) {
                case AND -> {
                    if (result) {
                        result = screeningRuleEvaluator.evaluate(rule, context);
                    }
                }
                case OR -> {
                    if (!result) {
                        result = screeningRuleEvaluator.evaluate(rule, context);
                    }
                }
            }
        }

        return result;
    }

    private void validateStructure(List<SearchConditionRule> rules) {
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("at least one SCREENING rule is required");
        }

        for (int index = 0; index < rules.size(); index++) {
            SearchConditionRule rule = rules.get(index);
            int expectedOrder = index + 1;
            if (rule.getRuleOrder() != expectedOrder) {
                throw new IllegalArgumentException(
                        "SCREENING ruleOrder must be consecutive from 1");
            }

            if (index == 0 && rule.getLogicalOperator() != null) {
                throw new IllegalArgumentException(
                        "the first SCREENING rule must not have a logicalOperator");
            }
            if (index > 0 && rule.getLogicalOperator() == null) {
                throw new IllegalArgumentException(
                        "subsequent SCREENING rules require a logicalOperator");
            }
        }
    }
}
