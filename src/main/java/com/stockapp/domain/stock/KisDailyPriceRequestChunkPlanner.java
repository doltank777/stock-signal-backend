package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyHistoryMissingRange;
import com.stockapp.domain.stock.dto.KisDailyPriceRequestChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class KisDailyPriceRequestChunkPlanner {

    static final int MAX_DAILY_PRICE_ROWS_PER_REQUEST = 100;

    private final KrxTradingCalendar tradingCalendar;

    public List<KisDailyPriceRequestChunk> plan(
            DailyHistoryMissingRange range) {
        Objects.requireNonNull(range, "range is required");

        List<LocalDate> tradingDays = tradingCalendar.tradingDaysBetween(
                range.startDate(),
                range.endDate());
        if (tradingDays.isEmpty()) {
            throw new IllegalArgumentException(
                    "range must contain at least one KRX trading day");
        }

        List<KisDailyPriceRequestChunk> chunks = new ArrayList<>();
        for (int startIndex = 0;
             startIndex < tradingDays.size();
             startIndex += MAX_DAILY_PRICE_ROWS_PER_REQUEST) {
            int endIndex = Math.min(
                    startIndex + MAX_DAILY_PRICE_ROWS_PER_REQUEST,
                    tradingDays.size()) - 1;
            chunks.add(new KisDailyPriceRequestChunk(
                    tradingDays.get(startIndex),
                    tradingDays.get(endIndex)));
        }
        return List.copyOf(chunks);
    }
}
