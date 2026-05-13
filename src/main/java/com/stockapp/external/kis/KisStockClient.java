package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisStockPriceResponse;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class KisStockClient {

    private final KisProperties kisProperties;
    private final KisAuthClient kisAuthClient;

    public KisStockPriceResponse getCurrentPrice(String stockCode) {
        String accessToken = kisAuthClient.getAccessToken();

        RestClient restClient = RestClient.builder()
                .baseUrl(kisProperties.getBaseUrl())
                .build();

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", kisProperties.getAppKey())
                .header("appsecret", kisProperties.getAppSecret())
                .header("tr_id", "FHKST01010100")
                .retrieve()
                .body(KisStockPriceResponse.class);
    }
}