package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisAuthClientTest {

    @Test
    void keepsExistingGeneralEnvironmentAndCacheKey() {
        KisProperties properties = new KisProperties();
        properties.setBaseUrl("https://paper.example.com");
        properties.setAppKey("paper-key");
        properties.setAppSecret("paper-secret");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        KisOAuthTokenClient oAuth = mock(KisOAuthTokenClient.class);
        when(oAuth.requestAccessToken(
                "https://paper.example.com", "paper-key", "paper-secret"))
                .thenReturn("paper-token");

        String token = new KisAuthClient(properties, redis, oAuth)
                .getAccessToken();

        assertThat(token).isEqualTo("paper-token");
        verify(values).get("kis:access-token");
        verify(values).set("kis:access-token", "paper-token",
                Duration.ofHours(23));
        verify(oAuth).requestAccessToken(
                "https://paper.example.com", "paper-key", "paper-secret");
    }
}
