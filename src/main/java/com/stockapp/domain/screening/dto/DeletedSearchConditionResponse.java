package com.stockapp.domain.screening.dto;

import com.stockapp.domain.screening.SearchCondition;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DeletedSearchConditionResponse {

    private Long id;
    private String name;
    private String description;
    private boolean enabled;
    private int priority;
    private int screeningScore;
    private boolean realtimeEnabled;
    private LocalDateTime deletedAt;
    private Long deletedById;
    private String deletedByEmail;

    public static DeletedSearchConditionResponse from(
            SearchCondition condition) {

        return DeletedSearchConditionResponse.builder()
                .id(condition.getId())
                .name(condition.getName())
                .description(condition.getDescription())
                .enabled(condition.isEnabled())
                .priority(condition.getPriority())
                .screeningScore(condition.getScreeningScore())
                .realtimeEnabled(condition.isRealtimeEnabled())
                .deletedAt(condition.getDeletedAt())
                .deletedById(
                        condition.getDeletedBy() != null
                                ? condition.getDeletedBy().getId()
                                : null)
                .deletedByEmail(
                        condition.getDeletedBy() != null
                                ? condition.getDeletedBy().getEmail()
                                : null)
                .build();
    }
}
