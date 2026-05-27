package com.stockapp.domain.signal.dto;

import com.stockapp.domain.signal.Signal;
import com.stockapp.domain.signal.SignalType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SignalResponse {

    private Long id;
    private String stockCode;
    private String stockName;
    private SignalType signalType;
    private String message;
    private LocalDateTime detectedAt;

    public static SignalResponse from(Signal signal) {
        return SignalResponse.builder()
                .id(signal.getId())
                .stockCode(signal.getStock().getStockCode())
                .stockName(signal.getStock().getStockName())
                .signalType(signal.getSignalType())
                .message(signal.getMessage())
                .detectedAt(signal.getDetectedAt())
                .build();
    }
}