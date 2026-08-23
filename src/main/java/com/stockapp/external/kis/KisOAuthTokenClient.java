package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisTokenRequest;
import com.stockapp.external.kis.dto.KisTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KisOAuthTokenClient {

    private final RestClient.Builder restClientBuilder;

    public KisOAuthTokenClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public String requestAccessToken(
            String baseUrl,
            String appKey,
            String appSecret
    ) {
        KisTokenRequest request = KisTokenRequest.builder()
                .grantType("client_credentials")
                .appkey(appKey)
                .appsecret(appSecret)
                .build();
        KisTokenResponse response = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .build()
                .post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KisTokenResponse.class);
        if (response == null || response.getAccessToken() == null
                || response.getAccessToken().isBlank()) {
            throw new IllegalStateException("KIS access token response is empty");
        }
        return response.getAccessToken();
    }
}
