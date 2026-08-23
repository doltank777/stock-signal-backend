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
    private TradingCalendar tradingCalendar = new TradingCalendar();
    private DailyPrice dailyPrice = new DailyPrice();

    public enum Environment {
        REAL,
        VIRTUAL
    }

    @Getter
    @Setter
    public static class TradingCalendar {
        private String baseUrl;
        private String appKey;
        private String appSecret;
        private long requestIntervalMs = 1000;
        private int maxPages = 50;
        private int rateLimitRetryMaxAttempts = 2;
        private long rateLimitRetryDelayMs = 61000;

        public void validateConfigured() {
            if (baseUrl == null || baseUrl.isBlank()
                    || appKey == null || appKey.isBlank()
                    || appSecret == null || appSecret.isBlank()) {
                throw new IllegalStateException(
                        "KIS Trading Calendar real environment is not configured; "
                                + "set base-url, app-key, and app-secret");
            }
            if (requestIntervalMs < 0 || rateLimitRetryDelayMs < 0) {
                throw new IllegalStateException(
                        "KIS Trading Calendar pacing delays must be >= 0");
            }
            if (maxPages < 1) {
                throw new IllegalStateException(
                        "KIS Trading Calendar max pages must be >= 1");
            }
            if (rateLimitRetryMaxAttempts < 1
                    || rateLimitRetryMaxAttempts > 2) {
                throw new IllegalStateException(
                        "KIS Trading Calendar rate-limit retry max attempts must be between 1 and 2");
            }
        }
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
