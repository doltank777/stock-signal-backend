package com.stockapp.external.kis;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!kis-websocket-probe")
@RequiredArgsConstructor
public class RedisKisWebSocketApprovalKeyCache
        implements KisWebSocketApprovalKeyCache {

    private final StringRedisTemplate redisTemplate;

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void put(String key, String approvalKey, Duration ttl) {
        redisTemplate.opsForValue().set(key, approvalKey, ttl);
    }
}
