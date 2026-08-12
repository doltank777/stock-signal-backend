package com.stockapp.domain.screening;

public enum ScreeningMetric {
    // 현재가
    CURRENT_PRICE,

    // 등락률
    CHANGE_RATE,

    // 현재 거래량
    VOLUME,

    // 평균 거래량
    AVERAGE_VOLUME,

    // 평균 거래량 대비 현재 거래량 비율
    VOLUME_RATIO,

    // 이동평균
    MOVING_AVERAGE
}