package com.stockapp.domain.stock;

import com.stockapp.domain.stock.dto.StockImportResult;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class StockImportService {

    private final StockRepository stockRepository;

    @Transactional
    public StockImportResult importStocks(MultipartFile file) {
        int totalRows = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                totalRows++;

                Row row = sheet.getRow(i);

                if (row == null) {
                    skippedCount++;
                    continue;
                }

                String stockName = getCellValue(row.getCell(0));
                String marketText = getCellValue(row.getCell(1));
                String stockCode = getCellValue(row.getCell(2));

                if (stockName.isBlank() || marketText.isBlank() || stockCode.isBlank()) {
                    skippedCount++;
                    continue;
                }

                MarketType marketType = convertMarketType(marketText);

                if (marketType == null) {
                    skippedCount++;
                    continue;
                }

                Stock stock = stockRepository.findByStockCode(stockCode)
                        .orElse(null);

                if (stock == null) {
                    Stock newStock = Stock.builder()
                            .stockCode(stockCode)
                            .stockName(stockName)
                            .marketType(marketType)
                            .build();

                    stockRepository.save(newStock);
                    insertedCount++;
                } else {
                    stock.updateStockInfo(stockName, marketType);
                    updatedCount++;
                }
            }

            return StockImportResult.builder()
                    .totalRows(totalRows)
                    .insertedCount(insertedCount)
                    .updatedCount(updatedCount)
                    .skippedCount(skippedCount)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("종목 엑셀 import 실패", e);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private MarketType convertMarketType(String marketText) {
        return switch (marketText.trim()) {
            case "유가" -> MarketType.KOSPI;
            case "코스닥" -> MarketType.KOSDAQ;
            case "코넥스" -> MarketType.KONEX;
            case "KOSPI" -> MarketType.KOSPI;
            case "KOSDAQ" -> MarketType.KOSDAQ;
            case "KONEX" -> MarketType.KONEX;
            default -> null;
        };
    }
}