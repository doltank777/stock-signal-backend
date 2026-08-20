package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class DailyPriceFinalizationTargetDateResolver {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public LocalDate resolveScheduledTargetDate() {
        return LocalDate.now(clock.withZone(KST));
    }
}
