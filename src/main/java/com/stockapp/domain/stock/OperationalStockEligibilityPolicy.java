package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OperationalStockEligibilityPolicy {

    private final SupportedInstrumentPolicy supportedInstrumentPolicy;

    public boolean isHistoryEligible(Stock stock) {
        if (stock == null) {
            return false;
        }
        return isTargetMarket(stock.getMarketType())
                && Boolean.TRUE.equals(stock.getPresentInLatestMaster())
                && supportedInstrumentPolicy.isSupported(
                        stock.getInstrumentType());
    }

    public boolean isCurrentEligible(Stock stock) {
        return isHistoryEligible(stock)
                && Boolean.FALSE.equals(stock.getSuspended())
                && Boolean.FALSE.equals(stock.getLiquidationTrading());
    }

    private boolean isTargetMarket(MarketType marketType) {
        return marketType == MarketType.KOSPI
                || marketType == MarketType.KOSDAQ;
    }
}
