package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRepository;
import com.stockapp.domain.signal.Signal;
import com.stockapp.domain.signal.SignalRepository;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeSignalPersistenceService {

    private static final int DUPLICATE_CHECK_MINUTES = 30;

    private final StockRepository stockRepository;
    private final SearchConditionRepository searchConditionRepository;
    private final SignalRepository signalRepository;

    @Transactional
    public void persistMatchedSignals(RealtimeSignalEvaluationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("result is required");
        }

        List<Long> matchedConditionIds = result.conditionResults().stream()
                .filter(RealtimeSignalConditionResult::matched)
                .map(RealtimeSignalConditionResult::conditionId)
                .toList();
        if (matchedConditionIds.isEmpty()) {
            return;
        }

        Stock stock = stockRepository.findById(result.stockId())
                .filter(found -> found.getStockCode().equals(result.stockCode()))
                .orElseThrow(() -> new IllegalStateException(
                        "realtime signal stock is missing or mismatched: "
                                + result.stockId()));
        Map<Long, SearchCondition> conditions = loadConditions(matchedConditionIds);
        for (Long conditionId : matchedConditionIds) {
            if (!conditions.containsKey(conditionId)) {
                throw new IllegalStateException(
                        "active realtime search condition is missing: " + conditionId);
            }
        }
        LocalDateTime duplicateThreshold = result.tradeDateTime()
                .minusMinutes(DUPLICATE_CHECK_MINUTES);

        for (Long conditionId : matchedConditionIds) {
            SearchCondition condition = conditions.get(conditionId);
            if (signalRepository.existsByStockAndSearchConditionAndDetectedAtAfter(
                    stock, condition, duplicateThreshold)) {
                continue;
            }

            Signal signal = Signal.createSearchConditionMatch(
                    stock, condition, result.tradeDateTime());
            signalRepository.save(signal);
            log.debug(
                    "condition-aware Signal 저장 - stockCode: {}, conditionId: {}, signalId: {}",
                    result.stockCode(), conditionId, signal.getId());
        }
    }

    private Map<Long, SearchCondition> loadConditions(List<Long> conditionIds) {
        List<SearchCondition> loaded = searchConditionRepository
                .findAllByIdInAndEnabledTrueAndRealtimeEnabledTrueAndDeletedAtIsNull(
                        conditionIds);
        Map<Long, SearchCondition> conditions = new LinkedHashMap<>();
        for (SearchCondition condition : loaded) {
            conditions.put(condition.getId(), condition);
        }
        return conditions;
    }
}
