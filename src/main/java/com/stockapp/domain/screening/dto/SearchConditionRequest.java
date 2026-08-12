package com.stockapp.domain.screening.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class SearchConditionRequest {

    @NotBlank(message = "검색식 이름은 필수입니다.")
    @Size(max = 100, message = "검색식 이름은 100자를 초과할 수 없습니다.")
    private String name;

    @Size(max = 500, message = "검색식 설명은 500자를 초과할 수 없습니다.")
    private String description;

    private boolean enabled;

    @Min(value = 0, message = "우선순위는 0 이상이어야 합니다.")
    @Max(value = 1000, message = "우선순위는 1000 이하여야 합니다.")
    private int priority;

    @Min(value = 0, message = "후보 점수는 0 이상이어야 합니다.")
    @Max(value = 100, message = "후보 점수는 100 이하여야 합니다.")
    private int screeningScore;

    private boolean realtimeEnabled;

    @Valid
    @NotEmpty(message = "검색식에는 최소 1개의 조건이 필요합니다.")
    private List<SearchConditionRuleRequest> rules;
}