package com.stockapp.domain.signal.dto;

import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.signal.Signal;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SignalResponse {

    private Long id;
    private String stockCode;
    private String stockName;
    private Long searchConditionId;
    private String searchConditionName;
    private String message;
    private LocalDateTime detectedAt;

    public static SignalResponse from(Signal signal) {
        SearchCondition searchCondition = signal.getSearchCondition();

        return SignalResponse.builder()
                .id(signal.getId())
                .stockCode(signal.getStock().getStockCode())
                .stockName(signal.getStock().getStockName())
                .searchConditionId(searchCondition.getId())
                .searchConditionName(searchCondition.getName())
                .message(signal.getMessage())
                .detectedAt(signal.getDetectedAt())
                .build();
    }
}
