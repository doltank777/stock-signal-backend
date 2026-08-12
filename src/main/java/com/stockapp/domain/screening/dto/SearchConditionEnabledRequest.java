package com.stockapp.domain.screening.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SearchConditionEnabledRequest {

    @NotNull(message = "활성 여부는 필수입니다.")
    private Boolean enabled;
}