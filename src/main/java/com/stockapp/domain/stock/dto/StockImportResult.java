package com.stockapp.domain.stock.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockImportResult {

    private int totalRows;
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;
}