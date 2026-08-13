package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.SearchConditionRequest;
import com.stockapp.domain.screening.dto.SearchConditionResponse;
import com.stockapp.domain.screening.dto.SearchConditionRuleRequest;
import com.stockapp.domain.screening.dto.DeletedSearchConditionResponse;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchConditionService {

    private final SearchConditionRepository searchConditionRepository;
    private final UserRepository userRepository;

    public List<SearchConditionResponse> getSearchConditions() {

        return searchConditionRepository
                .findAllByDeletedAtIsNullOrderByPriorityDescUpdatedAtDesc()
                .stream()
                .map(SearchConditionResponse::from)
                .toList();
    }

    public SearchConditionResponse getSearchCondition(Long id) {

        SearchCondition condition = getCondition(id);

        return SearchConditionResponse.from(condition);
    }

    @Transactional
    public SearchConditionResponse createSearchCondition(
            String email,
            SearchConditionRequest request) {

        User createdBy = getUser(email);

        validateRules(
                request.getRules(),
                request.isRealtimeEnabled());

        SearchCondition condition = SearchCondition.create(
                request.getName(),
                request.getDescription(),
                request.isEnabled(),
                request.getPriority(),
                request.getScreeningScore(),
                request.isRealtimeEnabled(),
                createdBy);

        request.getRules()
                .forEach(ruleRequest -> condition.addRule(
                        createRule(ruleRequest)));

        SearchCondition saved = searchConditionRepository.saveAndFlush(condition);

        return SearchConditionResponse.from(saved);
    }

    @Transactional
    public SearchConditionResponse updateSearchCondition(
            Long id,
            SearchConditionRequest request) {

        SearchCondition condition = getCondition(id);

        validateRules(
                request.getRules(),
                request.isRealtimeEnabled());

        condition.update(
                request.getName(),
                request.getDescription(),
                request.isEnabled(),
                request.getPriority(),
                request.getScreeningScore(),
                request.isRealtimeEnabled());

        List<SearchConditionRule> existingRules = new ArrayList<>(condition.getRules());

        existingRules.forEach(condition::removeRule);

        request.getRules()
                .forEach(ruleRequest -> condition.addRule(
                        createRule(ruleRequest)));

        searchConditionRepository.flush();

        return SearchConditionResponse.from(condition);
    }

    @Transactional
    public SearchConditionResponse changeEnabled(
            Long id,
            boolean enabled) {

        SearchCondition condition = getCondition(id);

        condition.changeEnabled(enabled);

        searchConditionRepository.flush();

        return SearchConditionResponse.from(condition);
    }

    public List<DeletedSearchConditionResponse> getDeletedSearchConditions() {

        return searchConditionRepository
                .findAllByDeletedAtIsNotNullOrderByDeletedAtDesc()
                .stream()
                .map(DeletedSearchConditionResponse::from)
                .toList();
    }

    @Transactional
    public void deleteSearchCondition(
            Long id,
            String email) {

        SearchCondition condition = getCondition(id);
        User deletedBy = getUser(email);

        condition.softDelete(deletedBy);

        searchConditionRepository.flush();
    }

    @Transactional
    public void restoreSearchCondition(Long id) {

        SearchCondition condition = searchConditionRepository
                .findByIdAndDeletedAtIsNotNull(id)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "삭제된 검색식을 찾을 수 없습니다."));

        condition.restore();

        searchConditionRepository.flush();
    }

    private SearchCondition getCondition(Long id) {

        return searchConditionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "검색식을 찾을 수 없습니다."));
    }

    private User getUser(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다."));
    }

    private SearchConditionRule createRule(
            SearchConditionRuleRequest request) {

        if (request.getRightType() == ScreeningRightType.VALUE) {

            return SearchConditionRule.createValueRule(
                    request.getStage(),
                    request.getLeftMetric(),
                    request.getLeftPeriod(),
                    request.getOperator(),
                    request.getRightValue(),
                    request.getLogicalOperator(),
                    request.getRuleOrder());
        }

        return SearchConditionRule.createMetricRule(
                request.getStage(),
                request.getLeftMetric(),
                request.getLeftPeriod(),
                request.getOperator(),
                request.getRightMetric(),
                request.getRightPeriod(),
                request.getLogicalOperator(),
                request.getRuleOrder());
    }

    private void validateRules(
            List<SearchConditionRuleRequest> rules,
            boolean realtimeEnabled) {

        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "검색식에는 최소 1개의 조건이 필요합니다.");
        }

        boolean hasScreeningRule = rules.stream()
                .anyMatch(
                        rule -> rule.getStage() == ScreeningStage.SCREENING);

        boolean hasSignalRule = rules.stream()
                .anyMatch(
                        rule -> rule.getStage() == ScreeningStage.SIGNAL);

        if (!hasScreeningRule) {
            throw new IllegalArgumentException(
                    "SCREENING 조건이 최소 1개 필요합니다.");
        }

        if (realtimeEnabled && !hasSignalRule) {
            throw new IllegalArgumentException(
                    "실시간 감시 검색식에는 SIGNAL 조건이 최소 1개 필요합니다.");
        }

        if (!realtimeEnabled && hasSignalRule) {
            throw new IllegalArgumentException(
                    "실시간 감시를 사용하지 않는 검색식에는 SIGNAL 조건을 등록할 수 없습니다.");
        }

        validateRuleOrders(
                rules,
                ScreeningStage.SCREENING);

        validateRuleOrders(
                rules,
                ScreeningStage.SIGNAL);

        rules.forEach(this::validateRule);
    }

    private void validateRuleOrders(
            List<SearchConditionRuleRequest> rules,
            ScreeningStage stage) {

        List<SearchConditionRuleRequest> stageRules = rules.stream()
                .filter(
                        rule -> rule.getStage() == stage)
                .sorted(
                        (first, second) -> Integer.compare(
                                first.getRuleOrder(),
                                second.getRuleOrder()))
                .toList();

        if (stageRules.isEmpty()) {
            return;
        }

        Set<Integer> ruleOrders = new HashSet<>();

        for (SearchConditionRuleRequest rule : stageRules) {

            if (!ruleOrders.add(rule.getRuleOrder())) {
                throw new IllegalArgumentException(
                        stage
                                + " 조건 순서가 중복되었습니다.");
            }
        }

        for (int index = 0; index < stageRules.size(); index++) {

            SearchConditionRuleRequest rule = stageRules.get(index);

            int expectedOrder = index + 1;

            if (rule.getRuleOrder() != expectedOrder) {
                throw new IllegalArgumentException(
                        stage
                                + " 조건 순서는 1부터 연속되어야 합니다.");
            }

            if (index == 0) {

                if (rule.getLogicalOperator() != null) {
                    throw new IllegalArgumentException(
                            stage
                                    + " 첫 번째 조건에는 논리 연산자를 지정할 수 없습니다.");
                }

            } else {

                if (rule.getLogicalOperator() == null) {
                    throw new IllegalArgumentException(
                            stage
                                    + " 두 번째 조건부터는 AND 또는 OR가 필요합니다.");
                }
            }
        }
    }

    private void validateRule(
            SearchConditionRuleRequest rule) {

        validateMetricPeriod(
                rule.getLeftMetric(),
                rule.getLeftPeriod(),
                "왼쪽 지표");

        if (rule.getRightType() == ScreeningRightType.VALUE) {

            if (rule.getRightValue() == null) {
                throw new IllegalArgumentException(
                        "비교 대상이 VALUE인 경우 비교 값은 필수입니다.");
            }

            if (rule.getRightMetric() != null
                    || rule.getRightPeriod() != null) {

                throw new IllegalArgumentException(
                        "비교 대상이 VALUE인 경우 오른쪽 지표와 기간을 지정할 수 없습니다.");
            }

            return;
        }

        if (rule.getRightMetric() == null) {
            throw new IllegalArgumentException(
                    "비교 대상이 METRIC인 경우 오른쪽 지표는 필수입니다.");
        }

        if (rule.getRightValue() != null) {
            throw new IllegalArgumentException(
                    "비교 대상이 METRIC인 경우 고정 값을 지정할 수 없습니다.");
        }

        validateMetricPeriod(
                rule.getRightMetric(),
                rule.getRightPeriod(),
                "오른쪽 지표");
    }

    private void validateMetricPeriod(
            ScreeningMetric metric,
            Integer period,
            String fieldName) {

        boolean periodRequired = metric == ScreeningMetric.AVERAGE_VOLUME
                || metric == ScreeningMetric.VOLUME_RATIO
                || metric == ScreeningMetric.MOVING_AVERAGE;

        if (periodRequired && period == null) {
            throw new IllegalArgumentException(
                    fieldName
                            + " "
                            + metric
                            + "에는 기간이 필요합니다.");
        }

        if (!periodRequired && period != null) {
            throw new IllegalArgumentException(
                    fieldName
                            + " "
                            + metric
                            + "에는 기간을 지정할 수 없습니다.");
        }
    }
}
