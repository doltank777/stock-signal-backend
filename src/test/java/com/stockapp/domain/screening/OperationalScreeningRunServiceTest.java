package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.screening.metric.OperationalScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.OperationalScreeningDataRequirements;
import com.stockapp.domain.screening.metric.OperationalScreeningDataMissingException;
import com.stockapp.domain.screening.metric.OperationalStockMetricContextFactory;
import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import com.stockapp.domain.stock.StockRepository;
import com.stockapp.domain.stock.TradingCalendarUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalScreeningRunServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);
    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);
    private static final List<MarketType> TARGET_MARKETS =
            List.of(MarketType.KOSPI, MarketType.KOSDAQ);

    @Mock OperationalScreeningEvaluationDateResolver readinessResolver;
    @Mock OperationalScreeningCompletenessService completenessService;
    @Mock SearchConditionRepository searchConditionRepository;
    @Mock StockRepository stockRepository;
    @Mock OperationalScreeningDataRequirementAnalyzer requirementAnalyzer;
    @Mock OperationalStockMetricContextFactory contextFactory;
    @Mock ScreeningEvaluationEngine evaluationEngine;
    @Mock ScreeningExecutionService screeningExecutionService;
    @Mock SearchCondition firstCondition;
    @Mock SearchCondition secondCondition;
    @Mock StockMetricContext firstContext;
    @Mock StockMetricContext secondContext;

    @Test
    void notTradingDayStopsBeforeEveryDownstreamStep() {
        when(readinessResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.notTradingDay(TODAY));

        var result = service().run();

        assertThat(result.status()).isEqualTo(
                OperationalScreeningRunStatus.NOT_TRADING_DAY);
        assertThat(result.evaluationDate()).isEmpty();
        assertThat(result.completeness()).isEmpty();
        assertThat(result.screeningResult()).isEmpty();
        verifyNoDownstreamInteractions();
    }

    @Test
    void finalizationNotReadyStopsBeforeCompletenessAndScreening() {
        when(readinessResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.finalizationNotReady(
                        TODAY, DATE));

        var result = service().run();

        assertThat(result.status()).isEqualTo(
                OperationalScreeningRunStatus.FINALIZATION_NOT_READY);
        assertThat(result.evaluationDate()).contains(DATE);
        verifyNoDownstreamInteractions();
    }

    @Test
    void calendarUnavailableRemainsFailClosed() {
        when(readinessResolver.resolve()).thenThrow(
                new TradingCalendarUnavailableException(TODAY, "missing"));

        assertThatThrownBy(() -> service().run())
                .isInstanceOf(TradingCalendarUnavailableException.class);

        verifyNoDownstreamInteractions();
    }

    @Test
    void incompleteDataPreservesMissingDetailsAndStopsScreening() {
        var completeness = incomplete();
        stubReady();
        when(completenessService.check(DATE)).thenReturn(completeness);

        var result = service().run();

        assertThat(result.status()).isEqualTo(
                OperationalScreeningRunStatus.DATA_INCOMPLETE);
        assertThat(result.evaluationDate()).contains(DATE);
        assertThat(result.completeness()).contains(completeness);
        assertThat(result.completeness().orElseThrow().missingStocks())
                .extracting(OperationalScreeningMissingStock::stockCode)
                .containsExactly("005930");
        assertThat(result.screeningResult()).isEmpty();
        verifyNoInteractions(searchConditionRepository, stockRepository,
                requirementAnalyzer, contextFactory, evaluationEngine);
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeDataRunsOperationalEvaluationOncePerStockWithExactDate() {
        var completeness = complete(2);
        List<SearchCondition> conditions =
                List.of(firstCondition, secondCondition);
        Stock first = stock(1L, "005930", MarketType.KOSPI);
        Stock second = stock(2L, "035720", MarketType.KOSDAQ);
        List<Stock> stocks = List.of(first, second);
        var requirements = new OperationalScreeningDataRequirements(20, true);
        ScreeningRunResult screeningResult = screeningResult(2);
        stubReady();
        when(completenessService.check(DATE)).thenReturn(completeness);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(conditions);
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(stocks);
        when(requirementAnalyzer.analyze(conditions)).thenReturn(requirements);
        when(contextFactory.createWithRequirements(first, requirements, DATE))
                .thenReturn(firstContext);
        when(contextFactory.createWithRequirements(second, requirements, DATE))
                .thenReturn(secondContext);
        when(evaluationEngine.evaluate(
                eq(stocks), eq(DATE), eq(conditions), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<Stock, StockMetricContext> provider =
                            invocation.getArgument(3);
                    assertThat(provider.apply(first)).isSameAs(firstContext);
                    assertThat(provider.apply(second)).isSameAs(secondContext);
                    return screeningResult;
                });

        var result = service().run();

        assertThat(result.status()).isEqualTo(
                OperationalScreeningRunStatus.COMPLETED);
        assertThat(result.screeningResult()).contains(screeningResult);
        verify(completenessService).check(DATE);
        verify(requirementAnalyzer).analyze(conditions);
        verify(contextFactory).createWithRequirements(
                first, requirements, DATE);
        verify(contextFactory).createWithRequirements(
                second, requirements, DATE);
    }

    @Test
    void noActiveConditionsCompletesWithoutRequirementsOrMarketData() {
        var completeness = complete(1);
        Stock stock = stock(1L, "005930", MarketType.KOSPI);
        ScreeningRunResult screeningResult = screeningResult(1);
        stubReady();
        when(completenessService.check(DATE)).thenReturn(completeness);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of());
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(List.of(stock));
        when(evaluationEngine.evaluateWithoutConditions(List.of(stock), DATE))
                .thenReturn(screeningResult);

        var result = service().run();

        assertThat(result.status()).isEqualTo(
                OperationalScreeningRunStatus.COMPLETED);
        verifyNoInteractions(requirementAnalyzer, contextFactory);
    }

    @Test
    void emptyTargetAfterCompleteCheckFailsClosed() {
        stubReady();
        when(completenessService.check(DATE)).thenReturn(complete(1));
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(firstCondition));
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target universe");

        verifyNoInteractions(requirementAnalyzer, contextFactory,
                evaluationEngine);
    }

    @Test
    void exactDateRowRaceBecomesPerStockFailureAndRunCompletes() {
        var completeness = complete(1);
        Stock stock = stock(1L, "005930", MarketType.KOSPI);
        var requirements = new OperationalScreeningDataRequirements(0, false);
        stubReady();
        when(completenessService.check(DATE)).thenReturn(completeness);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(firstCondition));
        when(stockRepository.findByMarketTypeInOrderByIdAsc(TARGET_MARKETS))
                .thenReturn(List.of(stock));
        when(requirementAnalyzer.analyze(List.of(firstCondition)))
                .thenReturn(requirements);
        when(contextFactory.createWithRequirements(stock, requirements, DATE))
                .thenThrow(new OperationalScreeningDataMissingException(
                        "finalized daily price disappeared"));
        ScreeningEvaluationEngine realEngine = new ScreeningEvaluationEngine(
                screeningExecutionService,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var service = new OperationalScreeningRunService(
                readinessResolver, completenessService,
                searchConditionRepository, stockRepository,
                requirementAnalyzer, contextFactory, realEngine);

        var result = service.run();

        assertThat(result.status()).isEqualTo(
                OperationalScreeningRunStatus.COMPLETED);
        assertThat(result.screeningResult().orElseThrow().failedStockCount())
                .isEqualTo(1);
        assertThat(result.screeningResult().orElseThrow().failures())
                .singleElement()
                .satisfies(failure -> {
                    assertThat(failure.stockCode()).isEqualTo("005930");
                    assertThat(failure.reason())
                            .isEqualTo("STOCK_MARKET_DATA_INVALID");
                });
        verifyNoInteractions(screeningExecutionService);
    }

    private OperationalScreeningRunService service() {
        return new OperationalScreeningRunService(
                readinessResolver, completenessService,
                searchConditionRepository, stockRepository,
                requirementAnalyzer, contextFactory, evaluationEngine);
    }

    private void stubReady() {
        when(readinessResolver.resolve()).thenReturn(
                OperationalScreeningReadinessResult.ready(TODAY, DATE));
    }

    private void verifyNoDownstreamInteractions() {
        verifyNoInteractions(completenessService, searchConditionRepository,
                stockRepository, requirementAnalyzer, contextFactory,
                evaluationEngine);
    }

    private OperationalScreeningCompletenessResult complete(int count) {
        return new OperationalScreeningCompletenessResult(
                DATE, OperationalScreeningCompletenessStatus.COMPLETE,
                count, count, 0, List.of());
    }

    private OperationalScreeningCompletenessResult incomplete() {
        return new OperationalScreeningCompletenessResult(
                DATE, OperationalScreeningCompletenessStatus.INCOMPLETE,
                2, 1, 1, List.of(new OperationalScreeningMissingStock(
                1L, "005930", "Samsung", MarketType.KOSPI)));
    }

    private ScreeningRunResult screeningResult(int count) {
        return new ScreeningRunResult(
                DATE, Instant.EPOCH, Instant.EPOCH,
                count, count, List.of(), List.of());
    }

    private Stock stock(Long id, String code, MarketType marketType) {
        return Stock.builder().id(id).stockCode(code).stockName(code)
                .marketType(marketType).build();
    }
}
