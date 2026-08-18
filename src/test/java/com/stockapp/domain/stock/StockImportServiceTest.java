package com.stockapp.domain.stock;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockImportServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Test
    void importsStringNumericAndFormulaStockCodesWithoutChangingTheirTextValues()
            throws Exception {
        when(stockRepository.findByStockCode(anyString())).thenReturn(Optional.empty());
        StockImportService service = new StockImportService(stockRepository);

        MockMultipartFile file = workbookWithSupportedCellTypes();

        service.importStocks(file);

        ArgumentCaptor<Stock> stocks = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository, org.mockito.Mockito.times(3)).save(stocks.capture());
        assertThat(stocks.getAllValues())
                .extracting(Stock::getStockCode)
                .containsExactly("005930", "35720", "000660");
    }

    private MockMultipartFile workbookWithSupportedCellTypes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0);
            createRow(sheet.createRow(1), "삼성전자", "KOSPI", "005930");

            Row numericCodeRow = sheet.createRow(2);
            createRow(numericCodeRow, "카카오", "KOSDAQ", null);
            numericCodeRow.createCell(2).setCellValue(35720);

            Row formulaCodeRow = sheet.createRow(3);
            createRow(formulaCodeRow, "SK하이닉스", "KOSPI", null);
            formulaCodeRow.createCell(2).setCellFormula("\"000660\"");
            workbook.getCreationHelper().createFormulaEvaluator()
                    .evaluateFormulaCell(formulaCodeRow.getCell(2));

            workbook.write(output);
            return new MockMultipartFile(
                    "file", "stocks.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }

    private void createRow(Row row, String name, String market, String code) {
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(market);
        if (code != null) {
            row.createCell(2).setCellValue(code);
        }
    }
}
