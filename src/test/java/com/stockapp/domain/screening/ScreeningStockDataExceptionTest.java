package com.stockapp.domain.screening;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreeningStockDataExceptionTest {

    @Test
    void preservesMessageAndCauseAsRuntimeException() {
        IllegalArgumentException cause = new IllegalArgumentException(
                "invalid snapshot");

        ScreeningStockDataException exception =
                new ScreeningStockDataException(
                        "stock data is invalid", cause);

        assertThat(exception)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("stock data is invalid")
                .hasCause(cause);
    }
}
