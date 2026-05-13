package com.stockapp.external.kis;

import com.stockapp.external.kis.dto.KisStockPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kis/stocks")
public class KisStockTestController {

    private final KisStockClient kisStockClient;

    @GetMapping("/{stockCode}/price")
    public KisStockPriceResponse getCurrentPrice(@PathVariable String stockCode) {
        return kisStockClient.getCurrentPrice(stockCode);
    }
}