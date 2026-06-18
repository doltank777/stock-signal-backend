package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisWebSocketApprovalRequest;
import com.stockapp.external.kis.dto.KisWebSocketApprovalResponse;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class KisWebSocketApprovalClient {

    private static final String KIS_WEBSOCKET_APPROVAL_KEY = "kis:websocket-approval-key";

    private final KisProperties kisProperties;
    private final StringRedisTemplate redisTemplate;

    public String getApprovalKey() {
        String cachedApprovalKey = redisTemplate.opsForValue().get(KIS_WEBSOCKET_APPROVAL_KEY);

        if (cachedApprovalKey != null && !cachedApprovalKey.isBlank()) {
            return cachedApprovalKey;
        }

        KisWebSocketApprovalResponse response = requestApprovalKey();

        redisTemplate.opsForValue().set(
                KIS_WEBSOCKET_APPROVAL_KEY,
                response.getApprovalKey(),
                Duration.ofHours(23)
        );

        return response.getApprovalKey();
    }

    private KisWebSocketApprovalResponse requestApprovalKey() {
        RestClient restClient = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
                .build();

        KisWebSocketApprovalRequest request = KisWebSocketApprovalRequest.builder()
                .grantType("client_credentials")
                .appkey(kisProperties.getAppKey())
                .secretkey(kisProperties.getAppSecret())
                .build();

        return restClient.post()
                .uri("/oauth2/Approval")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KisWebSocketApprovalResponse.class);
    }
}