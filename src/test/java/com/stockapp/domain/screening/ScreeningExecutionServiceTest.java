package com.stockapp.domain.screening;

import com.stockapp.domain.screening.metric.StockMetricContext;
import com.stockapp.domain.screening.metric.StockMetricContextFactory;
import com.stockapp.domain.screening.rule.ScreeningConditionEvaluator;
import com.stockapp.domain.stock.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningExecutionServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private StockMetricContextFactory stockMetricContextFactory;

    @Mock
    private ScreeningConditionEvaluator screeningConditionEvaluator;

    @Mock
    private Stock stock;

    @Mock
    private SearchCondition condition;

    @Mock
    private StockMetricContext context;

    private ScreeningExecutionService service;

    @BeforeEach
    void setUp() {
        service = new ScreeningExecutionService(
                stockMetricContextFactory,
                screeningConditionEvaluator);
    }

    @Test
    void returnsTrueUsingFactoryContextInExecutionOrder() {
        when(stockMetricContextFactory.create(stock, condition, BASE_DATE))
                .thenReturn(context);
        when(screeningConditionEvaluator.evaluate(condition, context))
                .thenReturn(true);

        assertThat(service.evaluate(stock, condition, BASE_DATE)).isTrue();

        InOrder inOrder = inOrder(
                stockMetricContextFactory,
                screeningConditionEvaluator);
        inOrder.verify(stockMetricContextFactory)
                .create(stock, condition, BASE_DATE);
        inOrder.verify(screeningConditionEvaluator)
                .evaluate(condition, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void returnsFalseAsNormalEvaluationResult() {
        when(stockMetricContextFactory.create(stock, condition, BASE_DATE))
                .thenReturn(context);
        when(screeningConditionEvaluator.evaluate(condition, context))
                .thenReturn(false);

        assertThat(service.evaluate(stock, condition, BASE_DATE)).isFalse();
        verify(stockMetricContextFactory)
                .create(stock, condition, BASE_DATE);
        verify(screeningConditionEvaluator)
                .evaluate(condition, context);
    }

    @Test
    void propagatesFactoryExceptionAndDoesNotCallEvaluator() {
        RuntimeException failure = new IllegalArgumentException(
                "context creation failed");
        when(stockMetricContextFactory.create(stock, condition, BASE_DATE))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.evaluate(stock, condition, BASE_DATE))
                .isSameAs(failure);
        verifyNoInteractions(screeningConditionEvaluator);
    }

    @Test
    void propagatesEvaluatorExceptionAfterOneFactoryCall() {
        RuntimeException failure = new IllegalStateException(
                "evaluation failed");
        when(stockMetricContextFactory.create(stock, condition, BASE_DATE))
                .thenReturn(context);
        when(screeningConditionEvaluator.evaluate(condition, context))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.evaluate(stock, condition, BASE_DATE))
                .isSameAs(failure);
        verify(stockMetricContextFactory)
                .create(stock, condition, BASE_DATE);
        verify(screeningConditionEvaluator)
                .evaluate(condition, context);
    }

    @Test
    void rejectsNullInputsBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.evaluate(null, condition, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.evaluate(stock, null, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.evaluate(stock, condition, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                stockMetricContextFactory,
                screeningConditionEvaluator);
    }

    @Test
    void evaluatesPreparedContextWithoutCallingFactory() {
        when(screeningConditionEvaluator.evaluate(condition, context))
                .thenReturn(true);

        assertThat(service.evaluate(condition, context)).isTrue();

        verify(screeningConditionEvaluator).evaluate(condition, context);
        verifyNoInteractions(stockMetricContextFactory);
    }

    @Test
    void returnsFalseFromPreparedContextEvaluation() {
        when(screeningConditionEvaluator.evaluate(condition, context))
                .thenReturn(false);

        assertThat(service.evaluate(condition, context)).isFalse();
    }

    @Test
    void rejectsNullPreparedContextInputsWithoutCallingDependencies() {
        assertThatThrownBy(() -> service.evaluate(null, context))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.evaluate(condition, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                stockMetricContextFactory,
                screeningConditionEvaluator);
    }

    @Test
    void propagatesPreparedContextEvaluatorException() {
        RuntimeException failure = new IllegalStateException(
                "evaluation failed");
        when(screeningConditionEvaluator.evaluate(condition, context))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.evaluate(condition, context))
                .isSameAs(failure);
        verifyNoInteractions(stockMetricContextFactory);
    }
}
