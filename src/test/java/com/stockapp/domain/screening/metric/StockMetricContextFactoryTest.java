package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningMetric;
import com.stockapp.domain.screening.ScreeningOperator;
import com.stockapp.domain.screening.ScreeningStage;
import com.stockapp.domain.screening.SearchCondition;
import com.stockapp.domain.screening.SearchConditionRule;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMarketDataQueryService;
import com.stockapp.domain.stock.dto.DailyPriceData;
import com.stockapp.domain.stock.dto.LatestStockSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMetricContextFactoryTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private StockMarketDataQueryService queryService;

    private Stock stock;
    private StockMetricContextFactory factory;
    private ScreeningDataRequirementAnalyzer requirementAnalyzer;

    @BeforeEach
    void setUp() {
        stock = Stock.builder()
                .id(1L)
                .stockCode("005930")
                .stockName("Samsung Electronics")
                .marketType(MarketType.KOSPI)
                .build();
        requirementAnalyzer = new ScreeningDataRequirementAnalyzer();
        factory = new StockMetricContextFactory(
                queryService, requirementAnalyzer);
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> factory.create(null, condition(), BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(stock, null, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(stock, condition(), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queryService);
    }

    @Test
    void rejectsConditionWithoutScreeningRules() {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SIGNAL,
                ScreeningMetric.CURRENT_PRICE, null));

        assertThatThrownBy(() -> factory.create(stock, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queryService);
    }

    @Test
    void snapshotOnlyMetricsUseOneSnapshotQueryAndNoDailyQuery() {
        SearchCondition condition = condition(
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CHANGE_RATE, null),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.VOLUME, null));
        Optional<LatestStockSnapshot> snapshot = snapshot();
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot);

        StockMetricContext context = factory.create(stock, condition, BASE_DATE);

        assertThat(context.snapshot()).isEqualTo(snapshot);
        assertThat(context.dailyPrices()).isEmpty();
        verify(queryService, times(1))
                .findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService, never()).findRecentDailyPricesBefore(
                org.mockito.ArgumentMatchers.eq(stock),
                org.mockito.ArgumentMatchers.eq(BASE_DATE),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void dailyOnlyMetricsUseMaximumPeriodInOneDailyQuery() {
        SearchCondition condition = condition(
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.AVERAGE_VOLUME, 5),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.MOVING_AVERAGE, 20));
        List<DailyPriceData> dailyPrices = dailyPrices(20);
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .thenReturn(dailyPrices);

        StockMetricContext context = factory.create(stock, condition, BASE_DATE);

        assertThat(context.snapshot()).isEmpty();
        assertThat(context.dailyPrices()).containsExactlyElementsOf(dailyPrices);
        verify(queryService, never()).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService, times(1))
                .findRecentDailyPricesBefore(stock, BASE_DATE, 20);
    }

    @Test
    void volumeRatioRequiresSnapshotAndDailyHistory() {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.VOLUME_RATIO, 20));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot());
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .thenReturn(dailyPrices(20));

        StockMetricContext context = factory.create(stock, condition, BASE_DATE);

        assertThat(context.snapshot()).isPresent();
        assertThat(context.dailyPrices()).hasSize(20);
        verify(queryService).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService).findRecentDailyPricesBefore(stock, BASE_DATE, 20);
    }

    @Test
    void metricRightSideContributesDailyRequirement() {
        SearchCondition condition = condition(metricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null,
                ScreeningMetric.MOVING_AVERAGE, 20));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot());
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .thenReturn(dailyPrices(20));

        factory.create(stock, condition, BASE_DATE);

        verify(queryService).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService).findRecentDailyPricesBefore(stock, BASE_DATE, 20);
    }

    @Test
    void metricRightSideContributesSnapshotRequirement() {
        SearchCondition condition = condition(metricRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.MOVING_AVERAGE, 20,
                ScreeningMetric.CURRENT_PRICE, null));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot());
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .thenReturn(dailyPrices(20));

        factory.create(stock, condition, BASE_DATE);

        verify(queryService).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService).findRecentDailyPricesBefore(stock, BASE_DATE, 20);
    }

    @Test
    void valueRightSideDoesNotContributeUnusedMetricFields() {
        SearchConditionRule rule = valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null);
        rule.updateValueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE,
                null,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ONE,
                null,
                1);
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot());

        StockMetricContext context = factory.create(
                stock, condition(rule), BASE_DATE);

        assertThat(context.dailyPrices()).isEmpty();
        verify(queryService, never()).findRecentDailyPricesBefore(
                org.mockito.ArgumentMatchers.eq(stock),
                org.mockito.ArgumentMatchers.eq(BASE_DATE),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void usesLargestDailyPeriodOnceAcrossRepeatedMetrics() {
        SearchCondition condition = condition(
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.AVERAGE_VOLUME, 5),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.AVERAGE_VOLUME, 10),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.VOLUME_RATIO, 20),
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.MOVING_AVERAGE, 60));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot());
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 60))
                .thenReturn(dailyPrices(60));

        factory.create(stock, condition, BASE_DATE);

        verify(queryService, times(1))
                .findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService, times(1))
                .findRecentDailyPricesBefore(stock, BASE_DATE, 60);
    }

    @Test
    void ignoresSignalMetricRequirements() {
        SearchCondition condition = condition(
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.MOVING_AVERAGE, 20),
                valueRule(ScreeningStage.SIGNAL,
                        ScreeningMetric.VOLUME_RATIO, 120));
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .thenReturn(dailyPrices(20));

        StockMetricContext context = factory.create(stock, condition, BASE_DATE);

        assertThat(context.snapshot()).isEmpty();
        verify(queryService, never()).findLatestSnapshotForDate(stock, BASE_DATE);
        verify(queryService).findRecentDailyPricesBefore(stock, BASE_DATE, 20);
    }

    @Test
    void preservesMissingAndInsufficientQueryResults() {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.VOLUME_RATIO, 60));
        List<DailyPriceData> fortyPrices = dailyPrices(40);
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(Optional.empty());
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 60))
                .thenReturn(fortyPrices);

        StockMetricContext context = factory.create(stock, condition, BASE_DATE);

        assertThat(context.snapshot()).isEmpty();
        assertThat(context.dailyPrices()).containsExactlyElementsOf(fortyPrices);
    }

    @Test
    void preservesEmptyDailyResult() {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.MOVING_AVERAGE, 20));
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 20))
                .thenReturn(List.of());

        assertThat(factory.create(stock, condition, BASE_DATE).dailyPrices())
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveDailyPeriod(int period) {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.MOVING_AVERAGE, period));

        assertThatThrownBy(() -> factory.create(stock, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @EnumSource(value = ScreeningMetric.class,
            names = {"AVERAGE_VOLUME", "VOLUME_RATIO", "MOVING_AVERAGE"})
    void rejectsMissingDailyPeriod(ScreeningMetric metric) {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING, metric, null));

        assertThatThrownBy(() -> factory.create(stock, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(queryService);
    }

    @Test
    void leavesSnapshotOnlyPeriodValidationToCalculator() {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, 5));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot());

        StockMetricContext context = factory.create(stock, condition, BASE_DATE);

        assertThat(context.snapshot()).isPresent();
        verify(queryService).findLatestSnapshotForDate(stock, BASE_DATE);
    }

    @Test
    void contextRejectsWrongStockSnapshotWithoutFactoryCorrection() {
        LatestStockSnapshot invalid = new LatestStockSnapshot(
                "000000", BASE_DATE,
                10_000L, 1.0, 100L, BASE_DATE.atStartOfDay());
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(Optional.of(invalid));

        assertThatThrownBy(() -> factory.create(stock, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextRejectsStaleSnapshotWithoutFactoryCorrection() {
        LatestStockSnapshot stale = new LatestStockSnapshot(
                "005930", BASE_DATE.minusDays(1),
                10_000L, 1.0, 100L, BASE_DATE.atStartOfDay());
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> factory.create(stock, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextRejectsUnsortedOrDuplicateDailyPricesWithoutFactoryCorrection() {
        SearchCondition condition = condition(valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.MOVING_AVERAGE, 2));
        List<DailyPriceData> unsorted = List.of(
                dailyPrice(BASE_DATE.minusDays(1)),
                dailyPrice(BASE_DATE.minusDays(2)));
        List<DailyPriceData> duplicate = List.of(
                dailyPrice(BASE_DATE.minusDays(2)),
                dailyPrice(BASE_DATE.minusDays(2)));
        when(queryService.findRecentDailyPricesBefore(stock, BASE_DATE, 2))
                .thenReturn(unsorted)
                .thenReturn(duplicate);

        assertThatThrownBy(() -> factory.create(stock, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(stock, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auxiliaryConditionFieldsDoNotChangeRequirements() {
        SearchConditionRule first = valueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE, null);
        SearchCondition low = condition(false, 0, 0, false, first);
        SearchCondition high = condition(true, 1000, 100, true,
                valueRule(ScreeningStage.SCREENING,
                        ScreeningMetric.CURRENT_PRICE, null));
        when(queryService.findLatestSnapshotForDate(stock, BASE_DATE))
                .thenReturn(snapshot());

        factory.create(stock, low, BASE_DATE);
        factory.create(stock, high, BASE_DATE);

        verify(queryService, times(2))
                .findLatestSnapshotForDate(stock, BASE_DATE);
    }

    private SearchCondition condition(SearchConditionRule... rules) {
        return condition(true, 100, 80, false, rules);
    }

    private SearchCondition condition(
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled,
            SearchConditionRule... rules
    ) {
        SearchCondition condition = SearchCondition.create(
                "condition", null, enabled, priority,
                screeningScore, realtimeEnabled, null);
        for (SearchConditionRule rule : rules) {
            condition.addRule(rule);
        }
        return condition;
    }

    private SearchConditionRule valueRule(
            ScreeningStage stage,
            ScreeningMetric metric,
            Integer period
    ) {
        return SearchConditionRule.createValueRule(
                stage, metric, period,
                ScreeningOperator.GREATER_THAN,
                BigDecimal.ONE, null, 1);
    }

    private SearchConditionRule metricRule(
            ScreeningStage stage,
            ScreeningMetric leftMetric,
            Integer leftPeriod,
            ScreeningMetric rightMetric,
            Integer rightPeriod
    ) {
        return SearchConditionRule.createMetricRule(
                stage, leftMetric, leftPeriod,
                ScreeningOperator.GREATER_THAN,
                rightMetric, rightPeriod,
                null, 1);
    }

    private Optional<LatestStockSnapshot> snapshot() {
        return Optional.of(new LatestStockSnapshot(
                "005930", BASE_DATE,
                70_000L, 1.5, 1_000L,
                LocalDateTime.of(2026, 8, 17, 12, 0)));
    }

    private List<DailyPriceData> dailyPrices(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> dailyPrice(
                        BASE_DATE.minusDays(count - index)))
                .toList();
    }

    private DailyPriceData dailyPrice(LocalDate tradeDate) {
        return new DailyPriceData(tradeDate, 60_000L, 1_000L);
    }
}
