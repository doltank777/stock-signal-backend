package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.screening.metric.ScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.ScreeningDataRequirements;
import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockMarketDataQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningPerformanceStructureTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private SearchConditionRepository searchConditionRepository;

    @Mock
    private ScreeningDataRequirementAnalyzer requirementAnalyzer;

    @Mock
    private StockMetricContextFactory stockMetricContextFactory;

    @Mock
    private ScreeningExecutionService screeningExecutionService;

    @Mock
    private StockMetricContext context;

    private ScreeningRunService runService;

    @BeforeEach
    void setUp() {
        runService = new ScreeningRunService(
                searchConditionRepository,
                requirementAnalyzer,
                stockMetricContextFactory,
                new ScreeningEvaluationEngine(
                        screeningExecutionService,
                        Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"),
                                ZoneOffset.UTC)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void conditionGrowthOnlyIncreasesEvaluationCalls(int conditionCount) {
        Stock stock = stock(0);
        List<SearchCondition> conditions = conditions(conditionCount);
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(true, 20);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(conditions);
        when(requirementAnalyzer.analyze(conditions))
                .thenReturn(requirements);
        when(stockMetricContextFactory.createWithRequirements(
                stock, requirements, BASE_DATE))
                .thenReturn(context);

        runService.run(List.of(stock), BASE_DATE);

        verify(searchConditionRepository, times(1))
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        verify(requirementAnalyzer, times(1)).analyze(conditions);
        verify(stockMetricContextFactory, times(1))
                .createWithRequirements(stock, requirements, BASE_DATE);
        verify(screeningExecutionService, times(conditionCount))
                .evaluate(any(SearchCondition.class), eq(context));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void stockGrowthLinearlyIncreasesContextsAndEvaluations(int stockCount) {
        List<Stock> stocks = stocks(stockCount);
        List<SearchCondition> conditions = conditions(3);
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(true, 20);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(conditions);
        when(requirementAnalyzer.analyze(conditions))
                .thenReturn(requirements);
        when(stockMetricContextFactory.createWithRequirements(
                any(Stock.class), eq(requirements), eq(BASE_DATE)))
                .thenReturn(context);

        runService.run(stocks, BASE_DATE);

        verify(searchConditionRepository, times(1))
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        verify(requirementAnalyzer, times(1)).analyze(conditions);
        verify(stockMetricContextFactory, times(stockCount))
                .createWithRequirements(
                        any(Stock.class), eq(requirements), eq(BASE_DATE));
        verify(screeningExecutionService, times(stockCount * 3))
                .evaluate(any(SearchCondition.class), eq(context));
    }

    @Test
    void snapshotOnlyRequirementsQuerySnapshotOncePerStock() {
        FactoryFixture fixture = new FactoryFixture();
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(true, 0);

        fixture.createContexts(10, requirements);

        verify(fixture.queryService, times(10))
                .findLatestSnapshotForDate(any(Stock.class), eq(BASE_DATE));
        verify(fixture.queryService, never())
                .findRecentDailyPricesBefore(
                        any(Stock.class), eq(BASE_DATE), anyInt());
    }

    @Test
    void dailyOnlyRequirementsQueryDailyOncePerStock() {
        FactoryFixture fixture = new FactoryFixture();
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(false, 60);

        fixture.createContexts(10, requirements);

        verify(fixture.queryService, never())
                .findLatestSnapshotForDate(any(Stock.class), eq(BASE_DATE));
        verify(fixture.queryService, times(10))
                .findRecentDailyPricesBefore(
                        any(Stock.class), eq(BASE_DATE), eq(60));
    }

    @Test
    void combinedRequirementsQueryEachDataSourceOncePerStock() {
        FactoryFixture fixture = new FactoryFixture();
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(true, 120);

        fixture.createContexts(10, requirements);

        verify(fixture.queryService, times(10))
                .findLatestSnapshotForDate(any(Stock.class), eq(BASE_DATE));
        verify(fixture.queryService, times(10))
                .findRecentDailyPricesBefore(
                        any(Stock.class), eq(BASE_DATE), eq(120));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void candidateOutcomeDoesNotChangeContextCount(boolean matched) {
        List<Stock> stocks = stocks(10);
        List<SearchCondition> conditions = conditions(3);
        ScreeningDataRequirements requirements =
                new ScreeningDataRequirements(true, 20);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(conditions);
        when(requirementAnalyzer.analyze(conditions))
                .thenReturn(requirements);
        when(stockMetricContextFactory.createWithRequirements(
                any(Stock.class), eq(requirements), eq(BASE_DATE)))
                .thenReturn(context);
        when(screeningExecutionService.evaluate(
                any(SearchCondition.class), eq(context)))
                .thenReturn(matched);

        ScreeningRunResult result = runService.run(stocks, BASE_DATE);

        assertThat(result.candidateStockCount())
                .isEqualTo(matched ? 10 : 0);
        verify(stockMetricContextFactory, times(10))
                .createWithRequirements(
                        any(Stock.class), eq(requirements), eq(BASE_DATE));
        verify(screeningExecutionService, times(30))
                .evaluate(any(SearchCondition.class), eq(context));
    }

    private List<SearchCondition> conditions(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> mock(SearchCondition.class))
                .toList();
    }

    private List<Stock> stocks(int count) {
        return IntStream.range(0, count)
                .mapToObj(this::stock)
                .toList();
    }

    private Stock stock(int index) {
        return Stock.builder()
                .id((long) index + 1)
                .stockCode(String.format("%06d", index))
                .stockName("stock-" + index)
                .marketType(MarketType.KOSPI)
                .build();
    }

    private class FactoryFixture {

        private final StockMarketDataQueryService queryService =
                mock(StockMarketDataQueryService.class);
        private final StockMetricContextFactory factory =
                new StockMetricContextFactory(
                        queryService, mock(ScreeningDataRequirementAnalyzer.class));

        private void createContexts(
                int stockCount,
                ScreeningDataRequirements requirements
        ) {
            if (requirements.snapshotRequired()) {
                when(queryService.findLatestSnapshotForDate(
                        any(Stock.class), eq(BASE_DATE)))
                        .thenReturn(Optional.empty());
            }
            if (requirements.maxDailyPeriod() > 0) {
                when(queryService.findRecentDailyPricesBefore(
                        any(Stock.class), eq(BASE_DATE),
                        eq(requirements.maxDailyPeriod())))
                        .thenReturn(List.of());
            }
            for (Stock stock : stocks(stockCount)) {
                factory.createWithRequirements(stock, requirements, BASE_DATE);
            }
        }
    }
}
