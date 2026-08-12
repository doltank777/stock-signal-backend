package com.stockapp.domain.screening;

public enum ScreeningStage {
    // 전체 2,764개 중 WebSocket 후보를 찾는 1차 조건
    SCREENING,

    // WebSocket 실시간 데이터로 최종 Signal을 판단하는 조건
    SIGNAL
}