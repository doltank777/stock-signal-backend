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

class RedisKisTradingCalendarAccessTokenProviderTest {

    @Test
    void usesDedicatedRealCalendarCacheAndCredentials() {
        KisProperties properties = configuredProperties();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        KisOAuthTokenClient oAuth = mock(KisOAuthTokenClient.class);
        when(oAuth.requestAccessToken(
                "https://calendar.example.com", "calendar-key",
                "calendar-secret")).thenReturn("calendar-token");

        String token = new RedisKisTradingCalendarAccessTokenProvider(
                properties, redis, oAuth).getAccessToken();

        assertThat(token).isEqualTo("calendar-token");
        verify(values).get("kis:access-token:trading-calendar-real");
        verify(values).set("kis:access-token:trading-calendar-real",
                "calendar-token", Duration.ofHours(23));
        verify(oAuth).requestAccessToken(
                "https://calendar.example.com", "calendar-key",
                "calendar-secret");
    }

    private KisProperties configuredProperties() {
        KisProperties properties = new KisProperties();
        properties.getTradingCalendar().setBaseUrl(
                "https://calendar.example.com");
        properties.getTradingCalendar().setAppKey("calendar-key");
        properties.getTradingCalendar().setAppSecret("calendar-secret");
        return properties;
    }
}
