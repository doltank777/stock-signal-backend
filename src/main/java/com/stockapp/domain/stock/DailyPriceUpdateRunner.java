package com.stockapp.domain.stock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Profile("daily-price-update")
public class DailyPriceUpdateRunner implements ApplicationRunner {

    private final DailyPriceUpdateService dailyPriceUpdateService;
    private final String baseDate;
    private final String stockCode;

    public DailyPriceUpdateRunner(
            DailyPriceUpdateService dailyPriceUpdateService,
            @Value("${daily-price-update.base-date:}") String baseDate,
            @Value("${daily-price-update.stock-code:}") String stockCode
    ) {
        this.dailyPriceUpdateService = dailyPriceUpdateService;
        this.baseDate = baseDate;
        this.stockCode = stockCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        String targetStockCode = stockCode == null ? null : stockCode.trim();
        if (baseDate == null || baseDate.isBlank()) {
            if (targetStockCode == null || targetStockCode.isBlank()) {
                dailyPriceUpdateService.update();
            } else {
                dailyPriceUpdateService.updateStock(targetStockCode);
            }
            return;
        }

        LocalDate parsedBaseDate = LocalDate.parse(baseDate.trim());
        if (targetStockCode == null || targetStockCode.isBlank()) {
            dailyPriceUpdateService.update(parsedBaseDate);
        } else {
            dailyPriceUpdateService.updateStock(targetStockCode, parsedBaseDate);
        }
    }
}
