package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningStockDataException;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMarketDataQueryService;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMetricContextFactoryRequirementsTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private StockMarketDataQueryService queryService;

    @Mock
    private ScreeningDataRequirementAnalyzer analyzer;

    private Stock stock;
    private StockMetricContextFactory factory;

    @BeforeEach
    void setUp() {
        stock = Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("삼성전자")
                .marketType(MarketType.KOSPI)
                .build();
        factory = new StockMetricContextFactory(queryService, analyzer);
    }

    @Test
    void rejectsNullRequirementsBeforeQuerying() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.createWithRequirements(
                        stock, null, BASE_DATE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.createWithRequirements(null,
                        new ScreeningDataRequirements(false, 0), BASE_DATE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.createWithRequirements(stock,
                        new ScreeningDataRequirements(false, 0), null));
        verifyNoInteractions(queryService);
    }

    @Test
    void queriesSnapshotOnlyOnce() {
        Optional<LatestStockSnapshot> snapshot = Optional.of(snapshot());
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot);

        StockMetricContext context = factory.createWithRequirements(stock,
                new ScreeningDataRequirements(true, 0), BASE_DATE);

        assertThat(context.snapshot()).isEqualTo(snapshot);
        assertThat(context.dailyPrices()).isEmpty();
        verify(queryService).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService, never()).findRecentDailyPricesBefore(
                stock, BASE_DATE, 0);
    }

    @Test
    void queriesDailyOnlyOnceWithMaximumPeriod() {
        List<DailyPriceData> prices = dailyPrices(3);
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 3))
                .thenReturn(prices);

        StockMetricContext context = factory.createWithRequirements(stock,
                new ScreeningDataRequirements(false, 3), BASE_DATE);

        assertThat(context.snapshot()).isEmpty();
        assertThat(context.dailyPrices()).containsExactlyElementsOf(prices);
        verify(queryService, never()).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService).findRecentDailyPricesBefore(stock, BASE_DATE, 3);
    }

    @Test
    void queriesSnapshotAndDailyAtMostOnceEach() {
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(Optional.of(snapshot()));
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 2))
                .thenReturn(dailyPrices(2));

        StockMetricContext context = factory.createWithRequirements(stock,
                new ScreeningDataRequirements(true, 2), BASE_DATE);

        assertThat(context.snapshot()).isPresent();
        assertThat(context.dailyPrices()).hasSize(2);
        verify(queryService).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService).findRecentDailyPricesBefore(stock, BASE_DATE, 2);
    }

    @Test
    void doesNotQueryWhenNoMarketDataIsRequired() {
        StockMetricContext context = factory.createWithRequirements(stock,
                new ScreeningDataRequirements(false, 0), BASE_DATE);

        assertThat(context.snapshot()).isEmpty();
        assertThat(context.dailyPrices()).isEmpty();
        verifyNoInteractions(queryService);
    }

    @Test
    void preservesEmptySnapshotAndInsufficientDailyHistory() {
        List<DailyPriceData> onePrice = dailyPrices(1);
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(Optional.empty());
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .thenReturn(onePrice);

        StockMetricContext context = factory.createWithRequirements(stock,
                new ScreeningDataRequirements(true, 20), BASE_DATE);

        assertThat(context.snapshot()).isEmpty();
        assertThat(context.dailyPrices()).containsExactlyElementsOf(onePrice);
        assertThat(context.recentDailyPrices(20)).isEmpty();
    }

    @Test
    void propagatesContextInvariantViolationWithoutCorrection() {
        LatestStockSnapshot invalid = new LatestStockSnapshot(
                "000000", BASE_DATE, 1L, 1.0, 1L,
                BASE_DATE.atStartOfDay());
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(Optional.of(invalid));

        assertThatThrownBy(() -> factory.createWithRequirements(stock,
                new ScreeningDataRequirements(true, 0), BASE_DATE))
                .isInstanceOf(ScreeningStockDataException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    private LatestStockSnapshot snapshot() {
        return new LatestStockSnapshot(
                "005930", BASE_DATE, 70_000L, 1.5, 1_000L,
                BASE_DATE.atStartOfDay());
    }

    private List<DailyPriceData> dailyPrices(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new DailyPriceData(
                        BASE_DATE.minusDays(count - index),
                        60_000L, 1_000L))
                .toList();
    }
}
