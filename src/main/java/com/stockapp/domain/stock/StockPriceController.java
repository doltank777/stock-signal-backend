package com.stockapp.domain.stock;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks/prices")
@RequiredArgsConstructor
public class StockPriceController {

    private final StockPriceService stockPriceService;

    @PostMapping
    public StockPriceResponse savePrice(@Valid @RequestBody StockPriceRequest request) {
        return stockPriceService.savePrice(request);
    }

    @GetMapping("/{stockCode}/latest")
    public StockPriceResponse getLatestPrice(@PathVariable String stockCode) {
        return stockPriceService.getLatestPrice(stockCode);
    }
}