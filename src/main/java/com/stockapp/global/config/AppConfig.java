package com.stockapp.global.config;

import com.stockapp.domain.screening.realtime.OperationalRealtimeAutomationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        KisProperties.class,
        OperationalRealtimeAutomationProperties.class
})
public class AppConfig {
}
