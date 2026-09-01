package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationalStockUniverseService {

    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    private final StockRepository stockRepository;
    private final SupportedInstrumentPolicy supportedInstrumentPolicy;

    public List<Stock> findHistoryTargets() {
        return List.copyOf(stockRepository.findHistoryEligibleStocks(
                TARGET_MARKETS, supportedInstrumentPolicy.supportedTypes()));
    }

    public List<Stock> findCurrentTargets() {
        return List.copyOf(stockRepository.findCurrentEligibleStocks(
                TARGET_MARKETS, supportedInstrumentPolicy.supportedTypes()));
    }
}
