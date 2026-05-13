package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTokenRequest;
import com.stockapp.external.kis.dto.KisTokenResponse;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class KisAuthClient {

    private static final String KIS_ACCESS_TOKEN_KEY = "kis:access-token";

    private final KisProperties kisProperties;
    private final StringRedisTemplate redisTemplate;

    public String getAccessToken() {
        String cachedToken = redisTemplate.opsForValue().get(KIS_ACCESS_TOKEN_KEY);

        if (cachedToken != null && !cachedToken.isBlank()) {
            return cachedToken;
        }

        KisTokenResponse response = requestAccessToken();

        redisTemplate.opsForValue().set(
                KIS_ACCESS_TOKEN_KEY,
                response.getAccessToken(),
                Duration.ofHours(23)
        );

        return response.getAccessToken();
    }

    private KisTokenResponse requestAccessToken() {
        RestClient restClient = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
                .build();

        KisTokenRequest request = KisTokenRequest.builder()
                .grantType("client_credentials")
                .appkey(kisProperties.getAppKey())
                .appsecret(kisProperties.getAppSecret())
                .build();

        return restClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KisTokenResponse.class);
    }
}