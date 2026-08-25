package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.signal.realtime.RealtimeSignalConditionDefinition;
import com.stockapp.domain.signal.realtime.RealtimeSignalConditionDefinitionLoader;
import com.stockapp.domain.signal.realtime.RealtimeSignalRequirementAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OperationalDailyHistoryRequirementAnalyzer {

    private final SearchConditionRepository searchConditionRepository;
    private final OperationalScreeningDataRequirementAnalyzer screeningAnalyzer;
    private final RealtimeSignalConditionDefinitionLoader signalDefinitionLoader;
    private final RealtimeSignalRequirementAnalyzer signalAnalyzer;

    @Transactional(readOnly = true)
    public OperationalDailyHistoryRequirement analyze() {
        List<SearchCondition> activeConditions = searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        return analyze(activeConditions);
    }

    public OperationalDailyHistoryRequirement analyze(
            List<SearchCondition> activeConditions
    ) {
        OperationalScreeningDataRequirements screening =
                screeningAnalyzer.analyze(activeConditions);

        List<Long> realtimeConditionIds = activeConditions.stream()
                .filter(SearchCondition::isRealtimeEnabled)
                .map(SearchCondition::getId)
                .toList();
        List<RealtimeSignalConditionDefinition> signalDefinitions =
                realtimeConditionIds.isEmpty()
                        ? List.of()
                        : signalDefinitionLoader.load(realtimeConditionIds);
        int signalMaxHistoryPeriod = signalAnalyzer.requiredDailyPeriod(
                signalDefinitions);
        int screeningRequiredPreviousTradingDayCount =
                screening.requiredPreviousRowCount();
        int requiredPreviousTradingDayCount = Math.max(
                screeningRequiredPreviousTradingDayCount,
                signalMaxHistoryPeriod);

        return new OperationalDailyHistoryRequirement(
                screening.maxHistoryPeriod(),
                screeningRequiredPreviousTradingDayCount,
                signalMaxHistoryPeriod,
                signalMaxHistoryPeriod,
                requiredPreviousTradingDayCount,
                true);
    }
}
