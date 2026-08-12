package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.screening.ScreeningStage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Getter
@Builder
public class SearchConditionResponse {

    private Long id;

    private String name;

    private String description;

    private boolean enabled;

    private int priority;

    private int screeningScore;

    private boolean realtimeEnabled;

    private Long createdById;

    private String createdByEmail;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<SearchConditionRuleResponse> rules;

    public static SearchConditionResponse from(
            SearchCondition condition
    ) {

        List<SearchConditionRuleResponse> ruleResponses =
                condition.getRules()
                        .stream()
                        .sorted(
                                Comparator
                                        .comparingInt(
                                                (SearchConditionRule rule) ->
                                                        stageOrder(rule.getStage())
                                        )
                                        .thenComparingInt(
                                                SearchConditionRule::getRuleOrder
                                        )
                        )
                        .map(SearchConditionRuleResponse::from)
                        .toList();

        return SearchConditionResponse.builder()
                .id(condition.getId())
                .name(condition.getName())
                .description(condition.getDescription())
                .enabled(condition.isEnabled())
                .priority(condition.getPriority())
                .screeningScore(condition.getScreeningScore())
                .realtimeEnabled(condition.isRealtimeEnabled())
                .createdById(
                        condition.getCreatedBy() != null
                                ? condition.getCreatedBy().getId()
                                : null
                )
                .createdByEmail(
                        condition.getCreatedBy() != null
                                ? condition.getCreatedBy().getEmail()
                                : null
                )
                .createdAt(condition.getCreatedAt())
                .updatedAt(condition.getUpdatedAt())
                .rules(ruleResponses)
                .build();
    }

    private static int stageOrder(ScreeningStage stage) {

        if (stage == ScreeningStage.SCREENING) {
            return 0;
        }

        return 1;
    }
}