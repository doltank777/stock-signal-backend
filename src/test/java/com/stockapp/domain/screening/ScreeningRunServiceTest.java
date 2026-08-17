package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.ScreeningCandidate;
import com.stockapp.domain.screening.dto.ScreeningMatch;
import com.stockapp.domain.screening.dto.ScreeningRunResult;
import com.stockapp.domain.screening.metric.ScreeningDataRequirementAnalyzer;
import com.stockapp.domain.screening.metric.ScreeningDataRequirements;
import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.stock.MarketType;
import com.stockapp.domain.stock.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningRunServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);
    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");

    @Mock
    private SearchConditionRepository searchConditionRepository;

    @Mock
    private ScreeningDataRequirementAnalyzer requirementAnalyzer;

    @Mock
    private StockMetricContextFactory stockMetricContextFactory;

    @Mock
    private ScreeningExecutionService screeningExecutionService;

    @Mock
    private StockMetricContext firstContext;

    @Mock
    private StockMetricContext secondContext;

    @Mock
    private StockMetricContext thirdContext;

    private Stock firstStock;
    private Stock secondStock;
    private Stock thirdStock;
    private SearchCondition firstCondition;
    private SearchCondition secondCondition;
    private ScreeningDataRequirements requirements;
    private ScreeningRunService service;

    @BeforeEach
    void setUp() {
        firstStock = stock(1L, "000660");
        secondStock = stock(2L, "005930");
        thirdStock = stock(3L, "035420");
        firstCondition = condition("first", 80, 100, true);
        secondCondition = condition("second", 60, 50, false);
        requirements = new ScreeningDataRequirements(true, 20);
        service = new ScreeningRunService(
                searchConditionRepository,
                requirementAnalyzer,
                stockMetricContextFactory,
                screeningExecutionService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsInvalidInputsBeforeCallingDependencies() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.run(null, BASE_DATE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.run(List.of(firstStock), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.run(
                        Arrays.asList(firstStock, null), BASE_DATE));

        verifyNoInteractions(
                searchConditionRepository,
                requirementAnalyzer,
                stockMetricContextFactory,
                screeningExecutionService);
    }

    @Test
    void rejectsDuplicateStockCodeBeforeCallingDependencies() {
        Stock duplicate = stock(999L, firstStock.getStockCode());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.run(
                        List.of(firstStock, duplicate), BASE_DATE))
                .withMessage("duplicate stockCode: 000660");
        verifyNoInteractions(
                searchConditionRepository,
                requirementAnalyzer,
                stockMetricContextFactory,
                screeningExecutionService);
    }

    @Test
    void returnsEmptyRunWithoutLookingUpConditions() {
        ScreeningRunResult result = service.run(List.of(), BASE_DATE);

        assertThat(result.baseDate()).isEqualTo(BASE_DATE);
        assertThat(result.startedAt()).isEqualTo(NOW);
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.totalStockCount()).isZero();
        assertThat(result.evaluatedStockCount()).isZero();
        assertThat(result.candidateStockCount()).isZero();
        assertThat(result.totalMatchCount()).isZero();
        assertThat(result.failedStockCount()).isZero();
        verifyNoInteractions(
                searchConditionRepository,
                requirementAnalyzer,
                stockMetricContextFactory,
                screeningExecutionService);
    }

    @Test
    void treatsNoExecutableConditionsAsSuccessfulNoOpRun() {
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of());

        ScreeningRunResult result = service.run(
                List.of(firstStock, secondStock, thirdStock), BASE_DATE);

        assertThat(result.totalStockCount()).isEqualTo(3);
        assertThat(result.evaluatedStockCount()).isEqualTo(3);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.failures()).isEmpty();
        verifyNoInteractions(
                requirementAnalyzer,
                stockMetricContextFactory,
                screeningExecutionService);
    }

    @Test
    void createsCandidatesAndMatchesInStockAndConditionOrder() {
        prepareThreeStockRun();
        when(screeningExecutionService.evaluate(firstCondition, firstContext))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(secondCondition, firstContext))
                .thenReturn(false);
        when(screeningExecutionService.evaluate(firstCondition, secondContext))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(secondCondition, secondContext))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(firstCondition, thirdContext))
                .thenReturn(false);
        when(screeningExecutionService.evaluate(secondCondition, thirdContext))
                .thenReturn(false);

        ScreeningRunResult result = service.run(
                List.of(firstStock, secondStock, thirdStock), BASE_DATE);

        assertThat(result.totalStockCount()).isEqualTo(3);
        assertThat(result.evaluatedStockCount()).isEqualTo(3);
        assertThat(result.candidateStockCount()).isEqualTo(2);
        assertThat(result.totalMatchCount()).isEqualTo(3);
        assertThat(result.failedStockCount()).isZero();
        assertThat(result.candidates())
                .extracting(candidate -> candidate.stock().getStockCode())
                .containsExactly("000660", "005930");
        assertThat(result.candidates().get(0).matches())
                .extracting(ScreeningMatch::condition)
                .containsExactly(firstCondition);
        assertThat(result.candidates().get(1).matches())
                .extracting(ScreeningMatch::condition)
                .containsExactly(firstCondition, secondCondition);
    }

    @Test
    void snapshotsConditionFieldsIncludingDisabledRealtime() {
        prepareSingleStockRun();
        when(screeningExecutionService.evaluate(firstCondition, firstContext))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(secondCondition, firstContext))
                .thenReturn(true);

        ScreeningCandidate candidate = service.run(
                        List.of(firstStock), BASE_DATE)
                .candidates().getFirst();

        assertThat(candidate.baseDate()).isEqualTo(BASE_DATE);
        assertThat(candidate.matches()).satisfiesExactly(
                match -> assertMatch(match, firstCondition, 80, 100, true),
                match -> assertMatch(match, secondCondition, 60, 50, false));
    }

    @Test
    void analyzesOnceAndCreatesOneContextPerStock() {
        prepareThreeStockRun();

        service.run(List.of(firstStock, secondStock, thirdStock), BASE_DATE);

        List<SearchCondition> conditions = List.of(
                firstCondition, secondCondition);
        verify(requirementAnalyzer).analyze(conditions);
        verify(stockMetricContextFactory).createWithRequirements(
                firstStock, requirements, BASE_DATE);
        verify(stockMetricContextFactory).createWithRequirements(
                secondStock, requirements, BASE_DATE);
        verify(stockMetricContextFactory).createWithRequirements(
                thirdStock, requirements, BASE_DATE);
        verify(screeningExecutionService).evaluate(
                firstCondition, firstContext);
        verify(screeningExecutionService).evaluate(
                secondCondition, firstContext);
        verify(screeningExecutionService).evaluate(
                firstCondition, secondContext);
        verify(screeningExecutionService).evaluate(
                secondCondition, secondContext);
        verify(screeningExecutionService).evaluate(
                firstCondition, thirdContext);
        verify(screeningExecutionService).evaluate(
                secondCondition, thirdContext);
    }

    @Test
    void continuesEveryConditionAndStockAfterFalseResults() {
        prepareThreeStockRun();

        ScreeningRunResult result = service.run(
                List.of(firstStock, secondStock, thirdStock), BASE_DATE);

        assertThat(result.evaluatedStockCount()).isEqualTo(3);
        assertThat(result.candidates()).isEmpty();
        verify(screeningExecutionService).evaluate(
                secondCondition, firstContext);
        verify(screeningExecutionService).evaluate(
                firstCondition, secondContext);
        verify(screeningExecutionService).evaluate(
                secondCondition, thirdContext);
    }

    @Test
    void propagatesRepositoryOrAnalyzerExceptionBeforeContextCreation() {
        RuntimeException repositoryFailure = new IllegalStateException(
                "repository failed");
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenThrow(repositoryFailure);

        assertThatThrownBy(() -> service.run(List.of(firstStock), BASE_DATE))
                .isSameAs(repositoryFailure);
        verifyNoInteractions(
                requirementAnalyzer,
                stockMetricContextFactory,
                screeningExecutionService);

        org.mockito.Mockito.reset(searchConditionRepository);
        RuntimeException analyzerFailure = new IllegalArgumentException(
                "analysis failed");
        List<SearchCondition> conditions = List.of(firstCondition);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(conditions);
        when(requirementAnalyzer.analyze(conditions))
                .thenThrow(analyzerFailure);

        assertThatThrownBy(() -> service.run(List.of(firstStock), BASE_DATE))
                .isSameAs(analyzerFailure);
        verifyNoInteractions(
                stockMetricContextFactory,
                screeningExecutionService);
    }

    @Test
    void failsFastWhenSecondStockContextCreationFails() {
        RuntimeException failure = new IllegalStateException(
                "context failed");
        prepareConditions();
        when(stockMetricContextFactory.createWithRequirements(
                firstStock, requirements, BASE_DATE))
                .thenReturn(firstContext);
        when(stockMetricContextFactory.createWithRequirements(
                secondStock, requirements, BASE_DATE))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.run(
                List.of(firstStock, secondStock, thirdStock), BASE_DATE))
                .isSameAs(failure);
        verify(stockMetricContextFactory, never()).createWithRequirements(
                same(thirdStock), same(requirements), same(BASE_DATE));
        verify(screeningExecutionService, never()).evaluate(
                same(firstCondition), same(secondContext));
    }

    @Test
    void failsFastWhenConditionEvaluationFails() {
        RuntimeException failure = new IllegalStateException(
                "evaluation failed");
        prepareConditions();
        when(stockMetricContextFactory.createWithRequirements(
                firstStock, requirements, BASE_DATE))
                .thenReturn(firstContext);
        when(stockMetricContextFactory.createWithRequirements(
                secondStock, requirements, BASE_DATE))
                .thenReturn(secondContext);
        lenient().doThrow(failure).when(screeningExecutionService)
                .evaluate(secondCondition, secondContext);

        assertThatThrownBy(() -> service.run(
                List.of(firstStock, secondStock, thirdStock), BASE_DATE))
                .isSameAs(failure);

        InOrder inOrder = inOrder(screeningExecutionService);
        inOrder.verify(screeningExecutionService)
                .evaluate(firstCondition, firstContext);
        inOrder.verify(screeningExecutionService)
                .evaluate(secondCondition, firstContext);
        inOrder.verify(screeningExecutionService)
                .evaluate(firstCondition, secondContext);
        inOrder.verify(screeningExecutionService)
                .evaluate(secondCondition, secondContext);
        verify(screeningExecutionService, never()).evaluate(
                firstCondition, thirdContext);
    }

    @Test
    void isolatesStockDataFailureAndContinuesWithNextStock() {
        prepareConditions();
        when(stockMetricContextFactory.createWithRequirements(
                firstStock, requirements, BASE_DATE))
                .thenReturn(firstContext);
        ScreeningStockDataException stockFailure =
                new ScreeningStockDataException(
                        "invalid market data",
                        new IllegalArgumentException("invalid snapshot"));
        when(stockMetricContextFactory.createWithRequirements(
                secondStock, requirements, BASE_DATE))
                .thenThrow(stockFailure);
        when(stockMetricContextFactory.createWithRequirements(
                thirdStock, requirements, BASE_DATE))
                .thenReturn(thirdContext);
        when(screeningExecutionService.evaluate(
                firstCondition, firstContext))
                .thenReturn(true);

        ScreeningRunResult result = service.run(
                List.of(firstStock, secondStock, thirdStock), BASE_DATE);

        assertThat(result.totalStockCount()).isEqualTo(3);
        assertThat(result.evaluatedStockCount()).isEqualTo(2);
        assertThat(result.candidateStockCount()).isEqualTo(1);
        assertThat(result.totalMatchCount()).isEqualTo(1);
        assertThat(result.failedStockCount()).isEqualTo(1);
        assertThat(result.candidates())
                .extracting(candidate -> candidate.stock().getStockCode())
                .containsExactly("000660");
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.stockCode()).isEqualTo("005930");
            assertThat(failure.stockName()).isEqualTo("stock-005930");
            assertThat(failure.reason())
                    .isEqualTo("STOCK_MARKET_DATA_INVALID");
            assertThat(failure.message()).isEqualTo("invalid market data");
        });
        verify(screeningExecutionService).evaluate(
                secondCondition, thirdContext);
    }

    @Test
    void discardsPartialMatchesForExplicitStockDataFailure() {
        prepareSingleStockRun();
        when(screeningExecutionService.evaluate(
                firstCondition, firstContext))
                .thenReturn(true);
        ScreeningStockDataException failure =
                new ScreeningStockDataException(null,
                        new IllegalArgumentException("invalid data"));
        lenient().doThrow(failure).when(screeningExecutionService)
                .evaluate(secondCondition, firstContext);

        ScreeningRunResult result = service.run(
                List.of(firstStock), BASE_DATE);

        assertThat(result.evaluatedStockCount()).isZero();
        assertThat(result.candidates()).isEmpty();
        assertThat(result.totalMatchCount()).isZero();
        assertThat(result.failedStockCount()).isEqualTo(1);
        assertThat(result.failures().getFirst().message())
                .isEqualTo("종목 Screening 시장 데이터가 유효하지 않습니다.");
    }

    @Test
    void keepsConditionStructureErrorAsGlobalFailure() {
        prepareSingleStockRun();
        IllegalArgumentException failure = new IllegalArgumentException(
                "invalid condition structure");
        lenient().doThrow(failure).when(screeningExecutionService)
                .evaluate(secondCondition, firstContext);

        assertThatThrownBy(() -> service.run(
                List.of(firstStock, secondStock, thirdStock), BASE_DATE))
                .isSameAs(failure);
        verify(stockMetricContextFactory, never()).createWithRequirements(
                secondStock, requirements, BASE_DATE);
    }

    @Test
    void keepsDataAccessErrorAsGlobalFailure() {
        prepareConditions();
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(stockMetricContextFactory.createWithRequirements(
                firstStock, requirements, BASE_DATE))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.run(
                List.of(firstStock, secondStock), BASE_DATE))
                .isSameAs(failure);
        verify(stockMetricContextFactory, never()).createWithRequirements(
                secondStock, requirements, BASE_DATE);
        verifyNoInteractions(screeningExecutionService);
    }

    private void prepareSingleStockRun() {
        prepareConditions();
        when(stockMetricContextFactory.createWithRequirements(
                firstStock, requirements, BASE_DATE))
                .thenReturn(firstContext);
    }

    private void prepareThreeStockRun() {
        prepareConditions();
        when(stockMetricContextFactory.createWithRequirements(
                firstStock, requirements, BASE_DATE))
                .thenReturn(firstContext);
        when(stockMetricContextFactory.createWithRequirements(
                secondStock, requirements, BASE_DATE))
                .thenReturn(secondContext);
        when(stockMetricContextFactory.createWithRequirements(
                thirdStock, requirements, BASE_DATE))
                .thenReturn(thirdContext);
    }

    private void prepareConditions() {
        List<SearchCondition> conditions = List.of(
                firstCondition, secondCondition);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(conditions);
        when(requirementAnalyzer.analyze(conditions))
                .thenReturn(requirements);
    }

    private void assertMatch(
            ScreeningMatch match,
            SearchCondition condition,
            int score,
            int priority,
            boolean realtimeEnabled
    ) {
        assertThat(match.condition()).isSameAs(condition);
        assertThat(match.screeningScore()).isEqualTo(score);
        assertThat(match.priority()).isEqualTo(priority);
        assertThat(match.realtimeEnabled()).isEqualTo(realtimeEnabled);
    }

    private Stock stock(Long id, String stockCode) {
        return Stock.builder()
                .id(id)
                .stockCode(stockCode)
                .stockName("stock-" + stockCode)
                .marketType(MarketType.KOSPI)
                .build();
    }

    private SearchCondition condition(
            String name,
            int screeningScore,
            int priority,
            boolean realtimeEnabled
    ) {
        return SearchCondition.create(
                name, null, true, priority,
                screeningScore, realtimeEnabled, null);
    }
}
