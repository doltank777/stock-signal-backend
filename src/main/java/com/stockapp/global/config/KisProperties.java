package com.stockapp.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kis")
public class KisProperties {

    private Environment environment;
    private String baseUrl;
    private String webSocketUrl;
    private String appKey;
    private String appSecret;
    private DailyPrice dailyPrice = new DailyPrice();

    public enum Environment {
        REAL,
        VIRTUAL
    }

    @Getter
    @Setter
    public static class DailyPrice {
        private int targetTradingDays = 250;
        private int requestWindowMonths = 6;
        private long requestDelayMs = 1200;
        private int retryMaxAttempts = 3;
        private long retryInitialDelayMs = 2000;
        private int maxApiCallsPerStock = 10;
        private int maxLookbackYears = 3;
        private int progressLogInterval = 25;
        private Update update = new Update();
        private Finalization finalization = new Finalization();
    }

    @Getter
    @Setter
    public static class Update {
        private long requestDelayMs = 1200;
        private int maxCatchUpDays = 30;
        private Retry retry = new Retry();
        private Scheduler scheduler = new Scheduler();
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
        private long initialBackoffMs = 2000;
        private long multiplier = 2;
    }

    @Getter
    @Setter
    public static class Scheduler {
        private boolean enabled = false;
        private String cron = "0 20 16 * * MON-FRI";
    }

    @Getter
    @Setter
    public static class Finalization {
        private FinalizationScheduler scheduler = new FinalizationScheduler();
        private StartupRecovery startupRecovery = new StartupRecovery();
    }

    @Getter
    @Setter
    public static class FinalizationScheduler {
        private boolean enabled = false;
        private String cron = "-";
        private String zone = "Asia/Seoul";
    }

    @Getter
    @Setter
    public static class StartupRecovery {
        private boolean enabled = false;
    }
}
