package com.stockapp.domain.screening.realtime;

import com.stockapp.domain.stock.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class RealtimeScreeningBaseDateProvider {

    private final StockPriceRepository stockPriceRepository;

    public LocalDate latestBaseDate() {
        return stockPriceRepository.findLatestTradeDate()
                .orElseThrow(() -> new IllegalStateException(
                        "no stock price snapshot found for realtime screening"));
    }
}
