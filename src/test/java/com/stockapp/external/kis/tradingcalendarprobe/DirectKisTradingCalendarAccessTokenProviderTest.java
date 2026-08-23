package com.stockapp.external.kis.tradingcalendarprobe;

import com.stockapp.external.kis.KisOAuthTokenClient;
import com.stockapp.global.config.KisProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DirectKisTradingCalendarAccessTokenProviderTest {

    @Test
    void usesOnlyExplicitCalendarRealEnvironment() {
        KisProperties properties = configuredProperties();
        KisOAuthTokenClient oAuth = mock(KisOAuthTokenClient.class);
        when(oAuth.requestAccessToken(
                "https://calendar.example.com", "calendar-key",
                "calendar-secret")).thenReturn("calendar-token");

        String token = new DirectKisTradingCalendarAccessTokenProvider(
                properties, oAuth).getAccessToken();

        assertThat(token).isEqualTo("calendar-token");
        verify(oAuth).requestAccessToken(
                "https://calendar.example.com", "calendar-key",
                "calendar-secret");
    }

    @Test
    void missingCalendarCredentialsFailBeforeOAuth() {
        KisProperties properties = new KisProperties();
        KisOAuthTokenClient oAuth = mock(KisOAuthTokenClient.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new DirectKisTradingCalendarAccessTokenProvider(
                        properties, oAuth).getAccessToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trading Calendar real environment");
        verifyNoInteractions(oAuth);
    }

    private KisProperties configuredProperties() {
        KisProperties properties = new KisProperties();
        properties.setBaseUrl("https://paper.example.com");
        properties.setAppKey("paper-key");
        properties.setAppSecret("paper-secret");
        properties.getTradingCalendar().setBaseUrl(
                "https://calendar.example.com");
        properties.getTradingCalendar().setAppKey("calendar-key");
        properties.getTradingCalendar().setAppSecret("calendar-secret");
        return properties;
    }
}
