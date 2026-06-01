package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.StockImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stocks")
public class StockImportController {

    private final StockImportService stockImportService;

    @Value("${stock.import-key}")
    private String importKey;

    @PostMapping("/import")
    public StockImportResult importStocks(
            @RequestHeader("X-IMPORT-KEY") String requestImportKey,
            @RequestParam("file") MultipartFile file
    ) {
        if (!importKey.equals(requestImportKey)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "종목 Import 권한이 없습니다."
            );
        }

        return stockImportService.importStocks(file);
    }
}