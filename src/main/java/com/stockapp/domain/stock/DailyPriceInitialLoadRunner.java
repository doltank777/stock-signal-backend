package com.stockapp.domain.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("daily-price-load")
public class DailyPriceInitialLoadRunner implements ApplicationRunner {

    private final DailyPriceInitialLoader loader;
    private final String baseDate;
    private final String stockCode;

    public DailyPriceInitialLoadRunner(
            DailyPriceInitialLoader loader,
            @Value("${daily-price-load.base-date:}") String baseDate,
            @Value("${daily-price-load.stock-code:}") String stockCode
    ) {
        this.loader = loader;
        this.baseDate = baseDate;
        this.stockCode = stockCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (baseDate == null || baseDate.isBlank()) {
            if (stockCode == null || stockCode.isBlank()) {
                loader.load();
            } else {
                loader.loadStock(stockCode);
            }
            return;
        }
        LocalDate parsedBaseDate = LocalDate.parse(baseDate);
        if (stockCode == null || stockCode.isBlank()) {
            loader.load(parsedBaseDate);
        } else {
            loader.loadStock(stockCode, parsedBaseDate);
        }
    }
}
