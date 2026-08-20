package com.stockprobe.kisdailyprice;

import com.stockapp.external.kis.KisAccessTokenProvider;
import com.stockapp.external.kis.KisDailyPriceClient;
import com.stockapp.external.kis.dailypriceprobe.DirectKisAccessTokenProvider;
import com.stockapp.external.kis.dailypriceprobe.KisDailyPriceProbeProperties;
import com.stockapp.external.kis.dailypriceprobe.KisDailyPriceProbeRunner;
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
@EnableConfigurationProperties({KisProperties.class, KisDailyPriceProbeProperties.class})
public class KisDailyPriceProbeApplication {

    @Bean
    KisAccessTokenProvider kisAccessTokenProvider(KisProperties properties) {
        return new DirectKisAccessTokenProvider(properties);
    }

    @Bean
    KisDailyPriceClient kisDailyPriceClient(
            KisProperties properties,
            KisAccessTokenProvider tokenProvider
    ) {
        return new KisDailyPriceClient(properties, tokenProvider, RestClient.builder());
    }

    @Bean
    KisDailyPriceProbeRunner kisDailyPriceProbeRunner(
            KisDailyPriceProbeProperties properties,
            KisDailyPriceClient client,
            Clock clock
    ) {
        return new KisDailyPriceProbeRunner(properties, client, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                KisDailyPriceProbeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("kis-daily-price-probe");
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
