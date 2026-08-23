package com.stockapp.external.kis.tradingcalendarprobe;

import com.stockapp.external.kis.KisOAuthTokenClient;
import com.stockapp.external.kis.KisTradingCalendarAccessTokenProvider;
import com.stockapp.global.config.KisProperties;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DirectKisTradingCalendarAccessTokenProvider
        implements KisTradingCalendarAccessTokenProvider {

    private final KisProperties properties;
    private final KisOAuthTokenClient oAuthTokenClient;

    @Override
    public String getAccessToken() {
        KisProperties.TradingCalendar calendar =
                properties.getTradingCalendar();
        calendar.validateConfigured();
        return oAuthTokenClient.requestAccessToken(
                calendar.getBaseUrl(), calendar.getAppKey(),
                calendar.getAppSecret());
    }
}
