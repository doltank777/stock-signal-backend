package com.stockapp.external.kis.probe;

import com.stockapp.external.kis.KisWebSocketApprovalClient;
import com.stockapp.external.kis.KisWebSocketClient;
import com.stockapp.external.kis.KisWebSocketConnector;
import com.stockapp.external.kis.KisWebSocketControlResponseParser;
import com.stockapp.external.kis.KisWebSocketSubscriptionTracker;
import com.stockapp.global.config.KisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@EnableConfigurationProperties({
        KisProperties.class,
        KisWebSocketProbeProperties.class
})
@Import({
        KisWebSocketApprovalClient.class,
        KisWebSocketClient.class,
        KisWebSocketConnector.class,
        KisWebSocketControlResponseParser.class,
        KisWebSocketSubscriptionTracker.class,
        NoCacheKisWebSocketApprovalKeyCache.class,
        KisWebSocketProbeHandler.class,
        KisWebSocketProbeRunner.class
})
public class KisWebSocketProbeApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                KisWebSocketProbeApplication.class);
        application.setAdditionalProfiles("kis-websocket-probe");
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
