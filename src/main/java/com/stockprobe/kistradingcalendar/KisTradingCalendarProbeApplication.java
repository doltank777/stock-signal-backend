package com.stockprobe.kistradingcalendar;

import com.stockapp.external.kis.KisOAuthTokenClient;
import com.stockapp.external.kis.KisTradingCalendarAccessTokenProvider;
import com.stockapp.external.kis.KisTradingCalendarClient;
import com.stockapp.external.kis.KisTradingCalendarSleeper;
import com.stockapp.external.kis.tradingcalendarprobe.DirectKisTradingCalendarAccessTokenProvider;
import com.stockapp.external.kis.tradingcalendarprobe.KisTradingCalendarProbeProperties;
import com.stockapp.external.kis.tradingcalendarprobe.KisTradingCalendarProbeRunner;
import com.stockapp.global.config.KisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        KisProperties.class,
        KisTradingCalendarProbeProperties.class
})
public class KisTradingCalendarProbeApplication {

    @Bean
    KisTradingCalendarAccessTokenProvider kisTradingCalendarAccessTokenProvider(
            KisProperties properties,
            KisOAuthTokenClient oAuthTokenClient
    ) {
        return new DirectKisTradingCalendarAccessTokenProvider(
                properties, oAuthTokenClient);
    }

    @Bean
    KisOAuthTokenClient kisOAuthTokenClient() {
        return new KisOAuthTokenClient(RestClient.builder());
    }

    @Bean
    KisTradingCalendarClient kisTradingCalendarClient(
            KisProperties properties,
            KisTradingCalendarAccessTokenProvider tokenProvider,
            KisTradingCalendarSleeper sleeper
    ) {
        return new KisTradingCalendarClient(
                properties, tokenProvider, RestClient.builder(), sleeper);
    }

    @Bean
    KisTradingCalendarSleeper kisTradingCalendarSleeper() {
        return new KisTradingCalendarSleeper();
    }

    @Bean
    KisTradingCalendarProbeRunner kisTradingCalendarProbeRunner(
            KisTradingCalendarProbeProperties properties,
            KisTradingCalendarClient client,
            Clock clock
    ) {
        return new KisTradingCalendarProbeRunner(properties, client, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                KisTradingCalendarProbeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("kis-trading-calendar-probe");
        ConfigurableApplicationContext context = null;
        try {
            context = application.run(args);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }
}
