package com.stockapp.domain.screening;

import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockDailyPriceRepository;
import com.stockapp.domain.stock.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalScreeningCompletenessServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);
    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    @Mock StockRepository stockRepository;
    @Mock StockDailyPriceRepository stockDailyPriceRepository;

    @Test
    void completeWhenEveryOperationalTargetHasExactDateRow() {
        List<Stock> targets = targets();
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(targets);
        when(stockDailyPriceRepository.findStockIdsWithPriceOnDate(
                DATE, List.of(1L, 2L))).thenReturn(List.of(1L, 2L));

        var result = service().check(DATE);

        assertThat(result.status()).isEqualTo(
                OperationalScreeningCompletenessStatus.COMPLETE);
        assertThat(result.targetStockCount()).isEqualTo(2);
        assertThat(result.availableStockCount()).isEqualTo(2);
        assertThat(result.missingStockCount()).isZero();
        assertThat(result.missingStocks()).isEmpty();
    }

    @Test
    void incompleteWithDeterministicMissingStockDetails() {
        List<Stock> targets = targets();
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(targets);
        when(stockDailyPriceRepository.findStockIdsWithPriceOnDate(
                DATE, List.of(1L, 2L))).thenReturn(List.of(2L));

        var result = service().check(DATE);

        assertThat(result.status()).isEqualTo(
                OperationalScreeningCompletenessStatus.INCOMPLETE);
        assertThat(result.availableStockCount()).isEqualTo(1);
        assertThat(result.missingStockCount()).isEqualTo(1);
        assertThat(result.missingStocks()).containsExactly(
                new OperationalScreeningMissingStock(
                        1L, "005930", "Samsung", MarketType.KOSPI));
        assertThatThrownBy(() -> result.missingStocks().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void incompleteWhenNoTargetHasExactDateRow() {
        List<Stock> targets = targets();
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(targets);
        when(stockDailyPriceRepository.findStockIdsWithPriceOnDate(
                DATE, List.of(1L, 2L))).thenReturn(List.of());

        var result = service().check(DATE);

        assertThat(result.availableStockCount()).isZero();
        assertThat(result.missingStockCount()).isEqualTo(2);
        assertThat(result.missingStocks())
                .extracting(OperationalScreeningMissingStock::stockCode)
                .containsExactly("005930", "035720");
    }

    @Test
    void extraRowsCannotIncreaseAvailableTargetCount() {
        List<Stock> targets = targets();
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(targets);
        when(stockDailyPriceRepository.findStockIdsWithPriceOnDate(
                DATE, List.of(1L, 2L))).thenReturn(List.of(1L, 2L, 3L));

        var result = service().check(DATE);

        assertThat(result.status()).isEqualTo(
                OperationalScreeningCompletenessStatus.COMPLETE);
        assertThat(result.availableStockCount()).isEqualTo(2);
    }

    @Test
    void emptyOperationalUniverseIsIncompleteWithoutInvalidInQuery() {
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(List.of());

        var result = service().check(DATE);

        assertThat(result.status()).isEqualTo(
                OperationalScreeningCompletenessStatus.INCOMPLETE);
        assertThat(result.targetStockCount()).isZero();
        verify(stockDailyPriceRepository, never())
                .findStockIdsWithPriceOnDate(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsNullEvaluationDateBeforeAnyQuery() {
        assertThatThrownBy(() -> service().check(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluationDate");
        verify(stockRepository, never())
                .findByMarketTypeInOrderByIdAsc(TARGET_MARKETS);
    }

    private OperationalScreeningCompletenessService service() {
        return new OperationalScreeningCompletenessService(
                stockRepository, stockDailyPriceRepository);
    }

    private List<Stock> targets() {
        return List.of(
                stock(1L, "005930", "Samsung", MarketType.KOSPI),
                stock(2L, "035720", "Kakao", MarketType.KOSDAQ));
    }

    private Stock stock(
            Long id, String code, String name, MarketType marketType) {
        return Stock.builder()
                .id(id)
                .stockCode(code)
                .stockName(name)
                .marketType(marketType)
                .build();
    }
}
