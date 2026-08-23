package com.stockapp.external.kis.dailypriceprobe;

import com.stockapp.external.kis.KisAccessTokenProvider;
import com.stockapp.external.kis.KisOAuthTokenClient;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DirectKisAccessTokenProvider implements KisAccessTokenProvider {

    private final KisProperties kisProperties;
    private final KisOAuthTokenClient oAuthTokenClient;

    @Override
    public String getAccessToken() {
        return oAuthTokenClient.requestAccessToken(
                kisProperties.getBaseUrl(),
                kisProperties.getAppKey(),
                kisProperties.getAppSecret());
    }
}
