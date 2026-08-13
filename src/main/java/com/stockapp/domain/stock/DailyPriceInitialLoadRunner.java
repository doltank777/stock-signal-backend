package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("daily-price-load")
@RequiredArgsConstructor
public class DailyPriceInitialLoadRunner implements ApplicationRunner {

    private final DailyPriceInitialLoader loader;

    @Value("${daily-price-load.base-date:}")
    private String baseDate;

    @Override
    public void run(ApplicationArguments args) {
        if (baseDate == null || baseDate.isBlank()) {
            loader.load();
            return;
        }
        loader.load(LocalDate.parse(baseDate));
    }
}
