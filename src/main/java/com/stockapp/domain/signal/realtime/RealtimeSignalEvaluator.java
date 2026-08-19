package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.realtime.RealtimeWatchTarget;
import com.stockapp.domain.screening.rule.RuleEvaluationSupport;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricContext;
import com.stockapp.domain.signal.metric.RealtimeSignalMetricContextFactory;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RealtimeSignalEvaluator {

    private final RealtimeSignalConditionDefinitionCache definitionCache;
    private final RealtimeSignalRequirementAnalyzer requirementAnalyzer;
    private final RealtimeSignalMetricContextFactory contextFactory;
    private final RealtimeSignalRuleEvaluator ruleEvaluator;
    private final RuleEvaluationSupport evaluationSupport;

    public RealtimeSignalEvaluationResult evaluate(
            RealtimeWatchTarget target,
            KisRealtimeTradePrice trade) {
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(trade, "trade is required");
        if (!target.stockCode().equals(trade.getStockCode())) {
            throw new IllegalArgumentException(
                    "target and trade stockCode must match");
        }
        LocalDateTime tradeDateTime = Objects.requireNonNull(
                trade.getTradeDateTime(), "tradeDateTime is required");

        Map<Long, RealtimeSignalConditionDefinition> definitions =
                definitionCache.getAll(target.conditionIds());
        List<RealtimeSignalConditionDefinition> orderedDefinitions =
                target.conditionIds().stream()
                        .map(conditionId -> requireDefinition(
                                definitions, conditionId))
                        .toList();
        int requiredDailyPeriod = requirementAnalyzer.requiredDailyPeriod(
                orderedDefinitions);
        RealtimeSignalMetricContext context = contextFactory.create(
                target, trade, requiredDailyPeriod);

        List<RealtimeSignalConditionResult> results = new ArrayList<>();
        for (RealtimeSignalConditionDefinition definition : orderedDefinitions) {
            boolean matched = evaluationSupport.evaluateSequence(
                    definition.rules(),
                    RealtimeSignalRule::ruleOrder,
                    RealtimeSignalRule::logicalOperator,
                    rule -> ruleEvaluator.evaluate(rule, context),
                    "SIGNAL");
            results.add(new RealtimeSignalConditionResult(
                    definition.conditionId(), matched));
        }
        return new RealtimeSignalEvaluationResult(
                target.stockId(), target.stockCode(), tradeDateTime, results);
    }

    private RealtimeSignalConditionDefinition requireDefinition(
            Map<Long, RealtimeSignalConditionDefinition> definitions,
            Long conditionId) {
        RealtimeSignalConditionDefinition definition = definitions.get(conditionId);
        if (definition == null) {
            throw new IllegalStateException(
                    "realtime SIGNAL condition definition is missing: " + conditionId);
        }
        return definition;
    }
}
