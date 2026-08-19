package com.stockapp.domain.signal.realtime;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record RealtimeSignalConditionDefinition(
        Long conditionId,
        List<RealtimeSignalRule> rules
) {
    public RealtimeSignalConditionDefinition {
        Objects.requireNonNull(conditionId, "conditionId is required");
        rules = Objects.requireNonNull(rules, "rules are required").stream()
                .map(rule -> Objects.requireNonNull(rule, "rule is required"))
                .sorted(Comparator.comparingInt(RealtimeSignalRule::ruleOrder))
                .toList();
        if (rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one SIGNAL rule is required");
        }
        for (int index = 0; index < rules.size(); index++) {
            RealtimeSignalRule rule = rules.get(index);
            if (rule.ruleOrder() != index + 1) {
                throw new IllegalArgumentException(
                        "SIGNAL ruleOrder must be consecutive from 1");
            }
            if (index == 0 && rule.logicalOperator() != null) {
                throw new IllegalArgumentException(
                        "the first SIGNAL rule must not have a logicalOperator");
            }
            if (index > 0 && rule.logicalOperator() == null) {
                throw new IllegalArgumentException(
                        "subsequent SIGNAL rules require a logicalOperator");
            }
        }
    }
}
