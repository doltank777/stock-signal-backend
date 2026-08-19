package com.stockapp.domain.screening.rule;

import com.stockapp.domain.screening.ScreeningLogicalOperator;
import com.stockapp.domain.screening.ScreeningOperator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

@Component
public class RuleEvaluationSupport {

    public boolean compare(ScreeningOperator operator, int comparison) {
        if (operator == null) {
            throw new IllegalArgumentException("operator is required");
        }
        return switch (operator) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case EQUAL -> comparison == 0;
        };
    }

    public <T> boolean evaluateSequence(
            List<T> rules,
            ToIntFunction<T> ruleOrder,
            Function<T, ScreeningLogicalOperator> logicalOperator,
            Predicate<T> evaluator,
            String stageName
    ) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one " + stageName + " rule is required");
        }
        for (int index = 0; index < rules.size(); index++) {
            T rule = rules.get(index);
            if (ruleOrder.applyAsInt(rule) != index + 1) {
                throw new IllegalArgumentException(
                        stageName + " ruleOrder must be consecutive from 1");
            }
            ScreeningLogicalOperator logical = logicalOperator.apply(rule);
            if (index == 0 && logical != null) {
                throw new IllegalArgumentException(
                        "the first " + stageName + " rule must not have a logicalOperator");
            }
            if (index > 0 && logical == null) {
                throw new IllegalArgumentException(
                        "subsequent " + stageName + " rules require a logicalOperator");
            }
        }

        boolean result = evaluator.test(rules.getFirst());
        for (int index = 1; index < rules.size(); index++) {
            T rule = rules.get(index);
            switch (logicalOperator.apply(rule)) {
                case AND -> {
                    if (result) result = evaluator.test(rule);
                }
                case OR -> {
                    if (!result) result = evaluator.test(rule);
                }
            }
        }
        return result;
    }
}
