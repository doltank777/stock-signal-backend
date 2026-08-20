package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.KisAccessTokenProvider;
import com.stockapp.external.kis.dto.KisTokenRequest;
import com.stockapp.external.kis.dto.KisTokenResponse;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class DirectKisAccessTokenProvider implements KisAccessTokenProvider {

    private final KisProperties kisProperties;

    @Override
    public String getAccessToken() {
        KisTokenRequest request = KisTokenRequest.builder()
                .grantType("client_credentials")
                .appkey(kisProperties.getAppKey())
                .appsecret(kisProperties.getAppSecret())
                .build();

        KisTokenResponse response = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
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
