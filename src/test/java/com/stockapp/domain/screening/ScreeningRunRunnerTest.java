package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningRunRunnerTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 13);

    @Mock ScreeningRunService screeningRunService;
    @Mock StockRepository stockRepository;

    @Test
    void rejectsMissingStockCodes() {
        assertValidationFailure(null, "2026-08-13", "stock-codes is required");
    }

    @Test
    void rejectsBlankStockCodes() {
        assertValidationFailure("   ", "2026-08-13", "stock-codes is required");
    }

    @Test
    void rejectsEmptyStockCodeToken() {
        assertValidationFailure(
                "005930,,000660", "2026-08-13", "empty tokens");
    }

    @Test
    void rejectsDuplicateStockCodes() {
        assertValidationFailure(
                "005930,000660,005930", "2026-08-13", "duplicates");
    }

    @Test
    void rejectsMoreThanFiftyStockCodes() {
        String stockCodes = IntStream.rangeClosed(1, 51)
                .mapToObj(number -> "%06d".formatted(number))
                .collect(java.util.stream.Collectors.joining(","));

        assertValidationFailure(stockCodes, "2026-08-13", "at most 50");
    }

    @Test
    void rejectsMissingBaseDate() {
        assertValidationFailure("005930", null, "base-date is required");
    }

    @Test
    void rejectsInvalidBaseDate() {
        assertValidationFailure("005930", "2026/08/13", "YYYY-MM-DD");
    }

    @Test
    void rejectsUnknownStockBeforeScreeningStarts() throws Exception {
        Stock samsung = stock("005930", "삼성전자", MarketType.KOSPI);
        when(stockRepository.findByStockCodeInAndMarketTypeIn(
                List.of("005930", "999999"),
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(List.of(samsung));

        assertThatThrownBy(() -> runner(
                "005930,999999", "2026-08-13").run(arguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999999");
        verify(screeningRunService, never()).run(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsKonexStockBeforeScreeningStarts() throws Exception {
        when(stockRepository.findByStockCodeInAndMarketTypeIn(
                List.of("950000"),
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> runner(
                "950000", "2026-08-13").run(arguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in KOSPI/KOSDAQ");
        verify(screeningRunService, never()).run(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void runsOneStockOnceWithParsedBaseDate() throws Exception {
        Stock samsung = stock("005930", "삼성전자", MarketType.KOSPI);
        when(stockRepository.findByStockCodeInAndMarketTypeIn(
                List.of("005930"),
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(List.of(samsung));
        when(screeningRunService.run(List.of(samsung), BASE_DATE))
                .thenReturn(result(1));

        runner("005930", "2026-08-13").run(arguments());

        verify(screeningRunService).run(List.of(samsung), BASE_DATE);
    }

    @Test
    void preservesTrimmedInputOrderAfterBulkLookup() throws Exception {
        Stock naver = stock("035420", "NAVER", MarketType.KOSPI);
        Stock samsung = stock("005930", "삼성전자", MarketType.KOSPI);
        Stock hynix = stock("000660", "SK하이닉스", MarketType.KOSPI);
        List<String> requestedCodes = List.of("035420", "005930", "000660");
        when(stockRepository.findByStockCodeInAndMarketTypeIn(
                requestedCodes,
                List.of(MarketType.KOSPI, MarketType.KOSDAQ)))
                .thenReturn(List.of(hynix, naver, samsung));
        when(screeningRunService.run(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(BASE_DATE)))
                .thenReturn(result(3));

        runner("035420, 005930,000660", "2026-08-13").run(arguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Stock>> stocksCaptor = ArgumentCaptor.forClass(List.class);
        verify(screeningRunService).run(stocksCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(BASE_DATE));
        assertThat(stocksCaptor.getValue())
                .extracting(Stock::getStockCode)
                .containsExactly("035420", "005930", "000660");
    }

    private void assertValidationFailure(
            String stockCodes,
            String baseDate,
            String message
    ) {
        assertThatThrownBy(() -> runner(stockCodes, baseDate).run(arguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
        verify(stockRepository, never())
                .findByStockCodeInAndMarketTypeIn(
                        org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyList());
        verify(screeningRunService, never()).run(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any());
    }

    private ScreeningRunRunner runner(String stockCodes, String baseDate) {
        return new ScreeningRunRunner(
                screeningRunService, stockRepository, stockCodes, baseDate);
    }

    private DefaultApplicationArguments arguments() {
        return new DefaultApplicationArguments(new String[0]);
    }

    private ScreeningRunResult result(int stockCount) {
        Instant startedAt = Instant.parse("2026-08-13T00:00:00Z");
        return new ScreeningRunResult(
                BASE_DATE, startedAt, startedAt.plusMillis(100),
                stockCount, stockCount, List.of(), List.of());
    }

    private Stock stock(String code, String name, MarketType marketType) {
        return Stock.builder()
                .stockCode(code)
                .stockName(name)
                .marketType(marketType)
                .build();
    }
}
