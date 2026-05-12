package com.stockapp.domain.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/search")
    public List<StockResponse> searchStocks(@RequestParam String keyword) {
        return stockService.searchStocks(keyword);
    }

    @GetMapping("/{stockCode}")
    public StockResponse getStock(@PathVariable String stockCode) {
        return stockService.getStock(stockCode);
    }
}