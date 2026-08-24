package com.stockapp.domain.screening.realtime;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalTime;

@Getter
@Setter
@ConfigurationProperties(prefix = "operational-screening.realtime")
public class OperationalRealtimeAutomationProperties {

    private LocalTime morningStart = LocalTime.of(8, 30);
    private LocalTime morningDeadline = LocalTime.of(8, 55);
    private Duration retryInterval = Duration.ofMinutes(5);
    private LocalTime marketOpen = LocalTime.of(9, 0);
    private LocalTime marketClose = LocalTime.of(15, 30);

    @PostConstruct
    public void validate() {
        if (morningStart == null || morningDeadline == null
                || retryInterval == null || marketOpen == null
                || marketClose == null) {
            throw new IllegalStateException(
                    "operational realtime times are required");
        }
        if (!morningStart.isBefore(morningDeadline)
                || !morningDeadline.isBefore(marketOpen)
                || marketOpen.isAfter(marketClose)) {
            throw new IllegalStateException(
                    "required time order is morning-start < morning-deadline "
                            + "< market-open <= market-close");
        }
        if (retryInterval.isZero() || retryInterval.isNegative()) {
            throw new IllegalStateException(
                    "retry-interval must be positive");
        }
    }
}
