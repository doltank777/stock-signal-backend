package com.stockbatch.tradingcalendar;

import com.stockapp.domain.stock.KrxTradingCalendarSynchronizer;
import com.stockapp.domain.stock.KrxTradingCalendarWriter;
import com.stockapp.domain.stock.KrxTradingDay;
import com.stockapp.domain.stock.KrxTradingDayRepository;
import com.stockapp.external.kis.KisOAuthTokenClient;
import com.stockapp.external.kis.KisTradingCalendarAccessTokenProvider;
import com.stockapp.external.kis.KisTradingCalendarClient;
import com.stockapp.external.kis.KisTradingCalendarSleeper;
import com.stockapp.external.kis.RedisKisTradingCalendarAccessTokenProvider;
import com.stockapp.global.config.KisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = KrxTradingDay.class)
@EnableJpaRepositories(basePackageClasses = KrxTradingDayRepository.class)
@EnableConfigurationProperties({
        KisProperties.class,
        TradingCalendarSyncProperties.class
})
public class TradingCalendarSyncApplication {

    @Bean
    KisOAuthTokenClient kisOAuthTokenClient(RestClient.Builder builder) {
        return new KisOAuthTokenClient(builder);
    }

    @Bean
    KisTradingCalendarAccessTokenProvider accessTokenProvider(
            KisProperties properties,
            StringRedisTemplate redisTemplate,
            KisOAuthTokenClient oAuthTokenClient
    ) {
        return new RedisKisTradingCalendarAccessTokenProvider(
                properties, redisTemplate, oAuthTokenClient);
    }

    @Bean
    KisTradingCalendarSleeper kisTradingCalendarSleeper() {
        return new KisTradingCalendarSleeper();
    }

    @Bean
    KisTradingCalendarClient kisTradingCalendarClient(
            KisProperties properties,
            KisTradingCalendarAccessTokenProvider tokenProvider,
            RestClient.Builder builder,
            KisTradingCalendarSleeper sleeper
    ) {
        return new KisTradingCalendarClient(
                properties, tokenProvider, builder, sleeper);
    }

    @Bean
    KrxTradingCalendarWriter krxTradingCalendarWriter(
            KrxTradingDayRepository repository
    ) {
        return new KrxTradingCalendarWriter(repository);
    }

    @Bean
    KrxTradingCalendarSynchronizer krxTradingCalendarSynchronizer(
            KisTradingCalendarClient client,
            KrxTradingCalendarWriter writer,
            Clock clock
    ) {
        return new KrxTradingCalendarSynchronizer(client, writer, clock);
    }

    @Bean
    TradingCalendarSyncRunner tradingCalendarSyncRunner(
            TradingCalendarSyncProperties properties,
            KrxTradingCalendarSynchronizer synchronizer
    ) {
        return new TradingCalendarSyncRunner(properties, synchronizer);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                TradingCalendarSyncApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("trading-calendar-sync");
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
