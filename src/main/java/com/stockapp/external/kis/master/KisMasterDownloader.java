package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KisMasterDownloader {

    private final RestClient.Builder restClientBuilder;

    public KisMasterDownloader(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public byte[] download(MarketType market) {
        KisMasterMarketSpec spec = KisMasterMarketSpec.from(market);
        try {
            byte[] body = restClientBuilder.build()
                    .get()
                    .uri(spec.downloadUri())
                    .retrieve()
                    .body(byte[].class);
            if (body == null || body.length == 0) {
                throw new KisMasterException("KIS Master response body is empty: " + market);
            }
            return body;
        } catch (KisMasterException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new KisMasterException("Failed to download KIS Master: " + market, exception);
        }
    }
}
