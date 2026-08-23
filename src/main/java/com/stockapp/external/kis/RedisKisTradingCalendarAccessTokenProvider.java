package com.stockapp.external.kis;

import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisKisTradingCalendarAccessTokenProvider
        implements KisTradingCalendarAccessTokenProvider {

    private static final String CACHE_KEY =
            "kis:access-token:trading-calendar-real";

    private final KisProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final KisOAuthTokenClient oAuthTokenClient;

    @Override
    public String getAccessToken() {
        KisProperties.TradingCalendar calendar =
                properties.getTradingCalendar();
        calendar.validateConfigured();
        String cachedToken = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cachedToken != null && !cachedToken.isBlank()) {
            return cachedToken;
        }
        String accessToken = oAuthTokenClient.requestAccessToken(
                calendar.getBaseUrl(), calendar.getAppKey(),
                calendar.getAppSecret());
        redisTemplate.opsForValue().set(
                CACHE_KEY, accessToken, Duration.ofHours(23));
        return accessToken;
    }
}
