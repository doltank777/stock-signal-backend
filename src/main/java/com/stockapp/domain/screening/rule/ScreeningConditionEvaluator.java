package com.stockapp.domain.screening.rule;

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
    private final RuleEvaluationSupport evaluationSupport;

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

        return evaluationSupport.evaluateSequence(
                rules,
                SearchConditionRule::getRuleOrder,
                SearchConditionRule::getLogicalOperator,
                rule -> screeningRuleEvaluator.evaluate(rule, context),
                "SCREENING");
    }
}
