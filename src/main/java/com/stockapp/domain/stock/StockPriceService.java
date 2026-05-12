package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockPriceService {

    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;

    @Transactional
    public StockPriceResponse savePrice(StockPriceRequest request) {
        if (!stockRepository.existsByStockCode(request.getStockCode())) {
            throw new IllegalArgumentException("등록되지 않은 종목입니다.");
        }

        StockPrice stockPrice = StockPrice.builder()
                .stockCode(request.getStockCode())
                .currentPrice(request.getCurrentPrice())
                .changeRate(request.getChangeRate())
                .volume(request.getVolume())
                .tradeDate(request.getTradeDate())
                .build();

        StockPrice savedPrice = stockPriceRepository.save(stockPrice);

        return StockPriceResponse.from(savedPrice);
    }

    public StockPriceResponse getLatestPrice(String stockCode) {
        StockPrice stockPrice = stockPriceRepository
                .findTopByStockCodeOrderByCollectedAtDesc(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("가격 정보를 찾을 수 없습니다."));

        return StockPriceResponse.from(stockPrice);
    }
}