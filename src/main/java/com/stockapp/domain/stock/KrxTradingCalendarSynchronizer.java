package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.KrxTradingCalendarSyncResult;
import com.stockapp.external.kis.KisTradingCalendarClient;
import com.stockapp.external.kis.dto.KisTradingDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KrxTradingCalendarSynchronizer {

    private final KisTradingCalendarClient client;
    private final KrxTradingCalendarWriter writer;
    private final Clock clock;

    public KrxTradingCalendarSyncResult synchronize(LocalDate baseDate) {
        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate is required");
        }
        Instant startedAt = Instant.now(clock);
        List<KisTradingDay> received = client.getTradingDays(baseDate);
        List<KisTradingDay> validated = validate(received);
        Instant synchronizedAt = Instant.now(clock);
        KrxTradingCalendarWriter.WriteResult writeResult =
                writer.write(validated, synchronizedAt);
        return new KrxTradingCalendarSyncResult(
                baseDate, received.size(), writeResult.inserted(),
                writeResult.updated(), writeResult.unchanged(),
                startedAt, Instant.now(clock));
    }

    public KrxTradingCalendarSyncResult synchronize(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null) {
            throw new IllegalArgumentException("startDate is required");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("endDate is required");
        }
        Instant startedAt = Instant.now(clock);
        List<KisTradingDay> received = client.getTradingDays(startDate, endDate);
        List<KisTradingDay> validated = validate(received);
        Instant synchronizedAt = Instant.now(clock);
        KrxTradingCalendarWriter.WriteResult writeResult =
                writer.write(validated, synchronizedAt);
        return new KrxTradingCalendarSyncResult(
                startDate, received.size(), writeResult.inserted(),
                writeResult.updated(), writeResult.unchanged(),
                startedAt, Instant.now(clock));
    }

    private List<KisTradingDay> validate(List<KisTradingDay> received) {
        if (received == null || received.isEmpty()) {
            throw new IllegalArgumentException(
                    "KIS trading calendar returned no dates");
        }
        Map<LocalDate, KisTradingDay> unique = new LinkedHashMap<>();
        for (KisTradingDay day : received) {
            if (day == null || day.tradeDate() == null) {
                throw new IllegalArgumentException(
                        "KIS trading calendar contains an invalid date row");
            }
            KisTradingDay previous = unique.putIfAbsent(day.tradeDate(), day);
            if (previous != null
                    && previous.tradingDay() != day.tradingDay()) {
                throw new IllegalArgumentException(
                        "conflicting KIS trading calendar date: "
                                + day.tradeDate());
            }
        }
        return List.copyOf(unique.values());
    }
}
