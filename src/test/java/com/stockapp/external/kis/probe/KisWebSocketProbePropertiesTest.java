package com.stockapp.external.kis.probe;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KisWebSocketProbePropertiesTest {

    @Test
    void normalizesTrimsAndDeduplicatesWhilePreservingOrder() {
        KisWebSocketProbeProperties properties = new KisWebSocketProbeProperties();
        properties.setStockCodes(" 005930,0088M0,005930 ");

        assertThat(properties.normalizedStockCodes())
                .containsExactly("005930", "0088M0");
    }

    @Test
    void rejectsMissingBlankAndEmptyTokens() {
        KisWebSocketProbeProperties properties = new KisWebSocketProbeProperties();
        assertThatIllegalArgumentException()
                .isThrownBy(properties::normalizedStockCodes)
                .withMessage("kis-websocket-probe.stock-codes is required");

        properties.setStockCodes(" ");
        assertThatIllegalArgumentException()
                .isThrownBy(properties::normalizedStockCodes);

        properties.setStockCodes("005930,,000660");
        assertThatIllegalArgumentException()
                .isThrownBy(properties::normalizedStockCodes)
                .withMessageContaining("empty tokens");
    }

    @Test
    void doesNotImposeCapacityOrNumericOnlyGuard() {
        KisWebSocketProbeProperties properties = new KisWebSocketProbeProperties();
        for (int count : new int[]{41, 44, 50}) {
            properties.setStockCodes(IntStream.range(0, count)
                    .mapToObj(index -> index == 0
                            ? "0088M0" : "%06d".formatted(index))
                    .collect(java.util.stream.Collectors.joining(",")));
            assertThat(properties.normalizedStockCodes()).hasSize(count);
        }
    }
}
