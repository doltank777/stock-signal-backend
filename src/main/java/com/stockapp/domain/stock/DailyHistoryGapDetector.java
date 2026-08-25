package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.DailyHistoryGap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
public class DailyHistoryGapDetector {

    private final StockDailyPriceRepository stockDailyPriceRepository;

    public DailyHistoryGap detect(
            Stock stock,
            List<LocalDate> requiredTradingDates
    ) {
        Objects.requireNonNull(stock, "stock is required");
        Objects.requireNonNull(requiredTradingDates,
                "requiredTradingDates is required");
        requiredTradingDates.forEach(date -> Objects.requireNonNull(date,
                "requiredTradingDates must not contain null"));

        List<LocalDate> requiredDates = new TreeSet<>(
                requiredTradingDates).stream().toList();
        if (requiredDates.isEmpty()) {
            return new DailyHistoryGap(List.of());
        }

        Set<LocalDate> existingDates = new HashSet<>(
                stockDailyPriceRepository.findTradeDates(
                        stock,
                        requiredDates.getFirst(),
                        requiredDates.getLast()));
        List<LocalDate> missingDates = requiredDates.stream()
                .filter(date -> !existingDates.contains(date))
                .toList();

        return new DailyHistoryGap(missingDates);
    }
}
