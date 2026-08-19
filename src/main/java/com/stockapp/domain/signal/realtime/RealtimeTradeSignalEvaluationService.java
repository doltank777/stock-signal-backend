package com.stockapp.domain.signal.realtime;

import com.stockapp.domain.screening.realtime.RealtimeWatchTargetRegistry;
import com.stockapp.external.kis.dto.KisRealtimeTradePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RealtimeTradeSignalEvaluationService {

    private final RealtimeWatchTargetRegistry targetRegistry;
    private final RealtimeSignalEvaluator signalEvaluator;

    public Optional<RealtimeSignalEvaluationResult> evaluate(
            KisRealtimeTradePrice trade) {
        Objects.requireNonNull(trade, "trade is required");
        String stockCode = Objects.requireNonNull(
                trade.getStockCode(), "trade stockCode is required");

        return targetRegistry.findByStockCode(stockCode)
                .map(target -> signalEvaluator.evaluate(target, trade));
    }
}
