package com.stockapp.domain.screening.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScreeningFailureTest {

    @Test
    void createsFailureWithStockIdentityAndReadableDetails() {
        ScreeningFailure failure = new ScreeningFailure(
                "005930", "삼성전자", "EVALUATION_FAILED", "daily price query failed");

        assertThat(failure.stockCode()).isEqualTo("005930");
        assertThat(failure.stockName()).isEqualTo("삼성전자");
        assertThat(failure.reason()).isEqualTo("EVALUATION_FAILED");
        assertThat(failure.message()).isEqualTo("daily price query failed");
    }

    @Test
    void rejectsNullOrBlankRequiredValues() {
        assertInvalid(null, "삼성전자", "FAILED", "message", "stockCode");
        assertInvalid(" ", "삼성전자", "FAILED", "message", "stockCode");
        assertInvalid("005930", null, "FAILED", "message", "stockName");
        assertInvalid("005930", " ", "FAILED", "message", "stockName");
        assertInvalid("005930", "삼성전자", null, "message", "reason");
        assertInvalid("005930", "삼성전자", " ", "message", "reason");
        assertInvalid("005930", "삼성전자", "FAILED", null, "message");
        assertInvalid("005930", "삼성전자", "FAILED", " ", "message");
    }

    private void assertInvalid(
            String stockCode,
            String stockName,
            String reason,
            String message,
            String fieldName
    ) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScreeningFailure(
                        stockCode, stockName, reason, message))
                .withMessage(fieldName + " is required");
    }
}
