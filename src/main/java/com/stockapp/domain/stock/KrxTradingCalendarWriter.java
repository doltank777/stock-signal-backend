package com.stockapp.domain.stock;

import com.stockapp.external.kis.dto.KisTradingDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KrxTradingCalendarWriter {

    private final KrxTradingDayRepository repository;

    @Transactional
    public WriteResult write(List<KisTradingDay> days, Instant synchronizedAt) {
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        for (KisTradingDay sourceDay : days) {
            KrxTradingDay existing = repository.findById(sourceDay.tradeDate())
                    .orElse(null);
            if (existing == null) {
                repository.save(KrxTradingDay.create(
                        sourceDay.tradeDate(), sourceDay.tradingDay(),
                        KrxTradingDay.KIS_SOURCE, synchronizedAt));
                inserted++;
            } else if (existing.updateFromSource(
                    sourceDay.tradingDay(), KrxTradingDay.KIS_SOURCE,
                    synchronizedAt)) {
                updated++;
            } else {
                unchanged++;
            }
        }
        repository.flush();
        return new WriteResult(inserted, updated, unchanged);
    }

    public record WriteResult(int inserted, int updated, int unchanged) {
    }
}
