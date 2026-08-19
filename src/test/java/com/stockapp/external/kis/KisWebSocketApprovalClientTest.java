package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KisWebSocketApprovalClientTest {

    @Test
    void separatesCacheKeysByEnvironmentWithoutExposingAppKey() {
        KisProperties properties = new KisProperties();
        properties.setAppKey("sensitive-app-key");
        KisWebSocketApprovalKeyCache approvalKeyCache =
                mock(KisWebSocketApprovalKeyCache.class);

        properties.setEnvironment(KisProperties.Environment.VIRTUAL);
        KisWebSocketApprovalClient virtualClient =
                new KisWebSocketApprovalClient(properties, approvalKeyCache);
        assertThat(virtualClient.approvalCacheKey())
                .isEqualTo("kis:websocket-approval-key:virtual")
                .doesNotContain("sensitive-app-key");

        properties.setEnvironment(KisProperties.Environment.REAL);
        KisWebSocketApprovalClient realClient =
                new KisWebSocketApprovalClient(properties, approvalKeyCache);
        assertThat(realClient.approvalCacheKey())
                .isEqualTo("kis:websocket-approval-key:real");
    }
}
