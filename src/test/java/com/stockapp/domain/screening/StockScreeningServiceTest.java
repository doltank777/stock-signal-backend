package com.stockapp.domain.screening;

import com.stockapp.domain.stock.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockScreeningServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private SearchConditionRepository searchConditionRepository;

    @Mock
    private ScreeningExecutionService screeningExecutionService;

    @Mock
    private Stock stock;

    @Mock
    private SearchCondition first;

    @Mock
    private SearchCondition second;

    @Mock
    private SearchCondition third;

    private StockScreeningService service;

    @BeforeEach
    void setUp() {
        service = new StockScreeningService(
                searchConditionRepository,
                screeningExecutionService);
    }

    @Test
    void returnsOnlyMatchedConditionsInRepositoryOrder() {
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(first, second, third));
        when(screeningExecutionService.evaluate(stock, first, BASE_DATE))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(stock, second, BASE_DATE))
                .thenReturn(false);
        when(screeningExecutionService.evaluate(stock, third, BASE_DATE))
                .thenReturn(true);

        List<SearchCondition> result = service.screen(stock, BASE_DATE);

        assertThat(result).containsExactly(first, third);
        InOrder inOrder = inOrder(
                searchConditionRepository,
                screeningExecutionService);
        inOrder.verify(searchConditionRepository)
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc();
        inOrder.verify(screeningExecutionService)
                .evaluate(stock, first, BASE_DATE);
        inOrder.verify(screeningExecutionService)
                .evaluate(stock, second, BASE_DATE);
        inOrder.verify(screeningExecutionService)
                .evaluate(stock, third, BASE_DATE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void returnsEmptyImmutableListWhenExecutableConditionDoesNotExist() {
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of());

        List<SearchCondition> result = service.screen(stock, BASE_DATE);

        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add(first))
                .isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(screeningExecutionService);
    }

    @Test
    void evaluatesEveryConditionWhenAllFail() {
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(first, second, third));
        when(screeningExecutionService.evaluate(stock, first, BASE_DATE))
                .thenReturn(false);
        when(screeningExecutionService.evaluate(stock, second, BASE_DATE))
                .thenReturn(false);
        when(screeningExecutionService.evaluate(stock, third, BASE_DATE))
                .thenReturn(false);

        assertThat(service.screen(stock, BASE_DATE)).isEmpty();
        verify(screeningExecutionService).evaluate(stock, first, BASE_DATE);
        verify(screeningExecutionService).evaluate(stock, second, BASE_DATE);
        verify(screeningExecutionService).evaluate(stock, third, BASE_DATE);
    }

    @Test
    void returnsEveryConditionWhenAllMatch() {
        List<SearchCondition> repositoryOrder = List.of(first, second, third);
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(repositoryOrder);
        when(screeningExecutionService.evaluate(stock, first, BASE_DATE))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(stock, second, BASE_DATE))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(stock, third, BASE_DATE))
                .thenReturn(true);

        List<SearchCondition> result = service.screen(stock, BASE_DATE);

        assertThat(result).containsExactlyElementsOf(repositoryOrder);
        assertThat(result).isNotSameAs(repositoryOrder);
    }

    @Test
    void continuesWithNextConditionAfterFalseResult() {
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(first, second));
        when(screeningExecutionService.evaluate(stock, first, BASE_DATE))
                .thenReturn(false);
        when(screeningExecutionService.evaluate(stock, second, BASE_DATE))
                .thenReturn(true);

        assertThat(service.screen(stock, BASE_DATE)).containsExactly(second);
        verify(screeningExecutionService).evaluate(stock, second, BASE_DATE);
    }

    @Test
    void preservesRepositoryOrderIncludingEqualPriorityOrder() {
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(third, first, second));
        when(screeningExecutionService.evaluate(
                org.mockito.ArgumentMatchers.eq(stock),
                org.mockito.ArgumentMatchers.any(SearchCondition.class),
                org.mockito.ArgumentMatchers.eq(BASE_DATE)))
                .thenReturn(true);

        assertThat(service.screen(stock, BASE_DATE))
                .containsExactly(third, first, second);
    }

    @Test
    void rejectsNullInputsBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.screen(null, BASE_DATE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.screen(stock, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                searchConditionRepository,
                screeningExecutionService);
    }

    @Test
    void propagatesRepositoryExceptionWithoutCallingExecutionService() {
        RuntimeException failure = new IllegalStateException(
                "repository failed");
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenThrow(failure);

        assertThatThrownBy(() -> service.screen(stock, BASE_DATE))
                .isSameAs(failure);
        verifyNoInteractions(screeningExecutionService);
    }

    @Test
    void failsFastWhenConditionEvaluationThrows() {
        RuntimeException failure = new IllegalArgumentException(
                "condition failed");
        when(searchConditionRepository
                .findAllByEnabledTrueAndDeletedAtIsNullOrderByPriorityDesc())
                .thenReturn(List.of(first, second, third));
        when(screeningExecutionService.evaluate(stock, first, BASE_DATE))
                .thenReturn(true);
        when(screeningExecutionService.evaluate(stock, second, BASE_DATE))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.screen(stock, BASE_DATE))
                .isSameAs(failure);
        verify(screeningExecutionService).evaluate(stock, first, BASE_DATE);
        verify(screeningExecutionService).evaluate(stock, second, BASE_DATE);
        verify(screeningExecutionService,
                org.mockito.Mockito.never()).evaluate(stock, third, BASE_DATE);
    }
}
