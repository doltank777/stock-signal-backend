package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stockRepository;

    public List<StockResponse> searchStocks(String keyword) {
        return stockRepository.findByStockNameContaining(keyword)
                .stream()
                .map(StockResponse::from)
                .toList();
    }

    public StockResponse getStock(String stockCode) {
        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다."));

        return StockResponse.from(stock);
    }
}